import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma.js';
import { requireAuth } from '../lib/auth.js';
import { readEntitlement } from './subscription.js';

/**
 * Eşitleme Pro'ya aittir. Uygulama da bunu denetliyor, ama kilit yalnızca
 * orada olursa değiştirilmiş bir istemci ya da aboneliği biten bir cihaz
 * sunucuyu kullanmaya devam edebilir.
 */
async function requirePro(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const entitlement = await readEntitlement(request.userId!);
  if (!entitlement.isPro) {
    return reply.code(403).send({
      error: 'subscription_required',
      message: 'Eşitleme Ders Defteri Pro ile kullanılabilir.',
    });
  }
}

/**
 * Çekme imlecinde bilerek bırakılan geriye dönük pay.
 *
 * `updatedAt` kaydın yazıldığı anda damgalanır ama işlem birkaç milisaniye
 * sonra commit olur. İmleci tam olarak sorgu anına kurarsak, sorgu sırasında
 * commit olan bir kayıt hiçbir zaman çekilmez. Bu pay sayesinde o kayıtlar bir
 * sonraki çekmede tekrar gelir; senkronizasyon zaten aynı kaydı iki kez
 * uygulamaya karşı dayanıklıdır.
 */
const CURSOR_SKEW_MS = 2000;

/// Tek istekte işlenecek azami kayıt sayısı (tür başına).
const MAX_BATCH = 500;

/**
 * Kimlikler küçük harfe çevrilir. iOS UUID'leri büyük harfle gönderir,
 * Postgres `uuid` sütunu küçük harfle döndürür. Sorgu ikisini eşleştirir ama
 * `applyBatch` içindeki harita metin karşılaştırır; çevrilmezse var olan her
 * kayıt yeni sanılır ve güncelleme yerine `create` benzersizlik hatası verir.
 */
const uuid = () => z.string().uuid().transform((s) => s.toLowerCase());

const base = {
  clientId: uuid(),
  clientUpdatedAt: z.coerce.date(),
  deletedAt: z.coerce.date().nullable().optional(),
};

const studentSchema = z.object({
  ...base,
  name: z.string().max(120).default(''),
  subject: z.string().max(120).default(''),
  grade: z.string().max(60).default(''),
  phone: z.string().max(40).default(''),
  parentName: z.string().max(120).default(''),
  parentPhone: z.string().max(40).default(''),
  hourlyRate: z.number().finite().min(0).max(1_000_000).default(0),
  startDate: z.coerce.date(),
  colorIndex: z.number().int().default(0),
  notes: z.string().max(4000).default(''),
  isArchived: z.boolean().default(false),
});

const lessonSchema = z.object({
  ...base,
  studentClientId: uuid().nullable().optional(),
  templateClientId: uuid().nullable().optional(),
  date: z.coerce.date(),
  duration: z.number().int().min(0).max(24 * 60).default(60),
  status: z.enum(['planned', 'completed', 'cancelled']).default('planned'),
  cancellationReason: z.enum(['none', 'student', 'teacher', 'makeup']).default('none'),
  topic: z.string().max(500).default(''),
  note: z.string().max(4000).default(''),
  feeOverride: z.number().finite().min(0).max(1_000_000).nullable().optional(),
  usesCustomFee: z.boolean().default(false),
});

const paymentSchema = z.object({
  ...base,
  studentClientId: uuid().nullable().optional(),
  date: z.coerce.date(),
  amount: z.number().finite().min(0).max(10_000_000).default(0),
  method: z.enum(['cash', 'transfer', 'other']).default('cash'),
  note: z.string().max(1000).default(''),
});

const homeworkSchema = z.object({
  ...base,
  studentClientId: uuid().nullable().optional(),
  title: z.string().max(300).default(''),
  detail: z.string().max(4000).default(''),
  assignedDate: z.coerce.date(),
  dueDate: z.coerce.date(),
  isDone: z.boolean().default(false),
  doneDate: z.coerce.date().nullable().optional(),
});

const templateSchema = z.object({
  ...base,
  studentClientId: uuid().nullable().optional(),
  weekday: z.number().int().min(1).max(7).default(3),
  hour: z.number().int().min(0).max(23).default(17),
  minute: z.number().int().min(0).max(59).default(0),
  duration: z.number().int().min(0).max(24 * 60).default(60),
  feeOverride: z.number().finite().min(0).max(1_000_000).nullable().optional(),
  usesCustomFee: z.boolean().default(false),
  isPaused: z.boolean().default(false),
  generatedUntil: z.coerce.date().nullable().optional(),
});

const pushBody = z.object({
  students: z.array(studentSchema).max(MAX_BATCH).default([]),
  lessons: z.array(lessonSchema).max(MAX_BATCH).default([]),
  payments: z.array(paymentSchema).max(MAX_BATCH).default([]),
  homeworks: z.array(homeworkSchema).max(MAX_BATCH).default([]),
  templates: z.array(templateSchema).max(MAX_BATCH).default([]),
});

interface Incoming {
  clientId: string;
  clientUpdatedAt: Date;
}

/**
 * Bir kayıt kümesini son yazan kazanır kuralıyla uygular.
 *
 * Sunucuda daha yeni bir sürüm varsa gelen kayıt sessizce atlanır; istemci bir
 * sonraki çekmede sunucudaki sürümü alır ve iki taraf kendiliğinden buluşur.
 */
async function applyBatch(
  // Prisma model delegate'leri ortak bir arayüz paylaşmadığı için burada
  // gevşek tiplenir; dışarıya açılan yüzey şemalarla zaten doğrulanmıştır.
  delegate: any,
  userId: string,
  rows: Incoming[],
): Promise<number> {
  if (rows.length === 0) return 0;

  // Aynı kayıt bir istekte iki kez gelirse yalnız en yenisi uygulanır.
  const latest = new Map<string, Incoming>();
  for (const row of rows) {
    const seen = latest.get(row.clientId);
    if (!seen || seen.clientUpdatedAt.getTime() <= row.clientUpdatedAt.getTime()) {
      latest.set(row.clientId, row);
    }
  }

  const existing: { clientId: string; clientUpdatedAt: Date }[] = await delegate.findMany({
    where: { userId, clientId: { in: [...latest.keys()] } },
    select: { clientId: true, clientUpdatedAt: true },
  });
  const existingMap = new Map(existing.map((e) => [e.clientId.toLowerCase(), e.clientUpdatedAt]));

  let applied = 0;
  for (const row of latest.values()) {
    const current = existingMap.get(row.clientId);
    if (current && current.getTime() >= row.clientUpdatedAt.getTime()) continue;

    const { clientId, ...data } = row;
    if (current) {
      await delegate.update({ where: { userId_clientId: { userId, clientId } }, data });
    } else {
      try {
        await delegate.create({ data: { ...data, clientId, userId } });
      } catch (error) {
        // Aynı cihazın eşzamanlı iki isteği aynı kaydı birlikte oluşturmaya
        // çalışabilir; ikincisi güncellemeye döner.
        if ((error as { code?: string }).code !== 'P2002') throw error;
        await delegate.updateMany({
          where: { userId, clientId, clientUpdatedAt: { lt: row.clientUpdatedAt } },
          data,
        });
      }
    }
    applied += 1;
  }
  return applied;
}

export async function syncRoutes(app: FastifyInstance) {
  /**
   * Değişiklikleri sunucuya yazar.
   *
   * Cihaz önce iter, sonra çeker. Böylece kendi değişiklikleri sunucuda
   * yerleşmeden önce sunucunun eski sürümüyle ezilmez.
   */
  app.post('/v1/sync', { preHandler: [requireAuth, requirePro] }, async (request, reply) => {
    const parsed = pushBody.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({
        error: 'invalid_body',
        message: 'Senkronizasyon verisi geçersiz.',
        detail: parsed.error.flatten().fieldErrors,
      });
    }

    const userId = request.userId!;
    const data = parsed.data;

    const applied =
      (await applyBatch(prisma.student, userId, data.students)) +
      (await applyBatch(prisma.lesson, userId, data.lessons)) +
      (await applyBatch(prisma.payment, userId, data.payments)) +
      (await applyBatch(prisma.homework, userId, data.homeworks)) +
      (await applyBatch(prisma.recurringTemplate, userId, data.templates));

    return reply.send({ ok: true, applied });
  });

  /**
   * `since` sonrası değişen her şeyi döndürür. `since` verilmezse ilk kurulum
   * kabul edilir ve tüm kayıtlar gönderilir.
   */
  app.get('/v1/sync', { preHandler: [requireAuth, requirePro] }, async (request, reply) => {
    const query = z.object({ since: z.coerce.date().optional() }).safeParse(request.query);
    if (!query.success) {
      return reply.code(400).send({ error: 'invalid_query', message: 'Geçersiz imleç.' });
    }

    const userId = request.userId!;
    const since = query.data.since;
    const nextCursor = new Date(Date.now() - CURSOR_SKEW_MS);
    const where = since ? { userId, updatedAt: { gt: since } } : { userId };

    const [students, lessons, payments, homeworks, templates] = await Promise.all([
      prisma.student.findMany({ where }),
      prisma.lesson.findMany({ where }),
      prisma.payment.findMany({ where }),
      prisma.homework.findMany({ where }),
      prisma.recurringTemplate.findMany({ where }),
    ]);

    return reply.send({
      cursor: nextCursor.toISOString(),
      students,
      lessons,
      payments,
      homeworks,
      templates,
    });
  });
}
