-- Ders paketleri ve dersin paket bağı. Yalnız ekleme; eski uygulama sürümleri
-- bu tablo ve sütunu bilmeden çalışmaya devam eder.

-- AlterTable
ALTER TABLE "lessons" ADD COLUMN "packageClientId" UUID;

-- CreateTable
CREATE TABLE "lesson_packages" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "clientId" UUID NOT NULL,
    "studentClientId" UUID,
    "startDate" TIMESTAMP(3) NOT NULL,
    "lessonCount" INTEGER NOT NULL DEFAULT 0,
    "price" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "note" TEXT NOT NULL DEFAULT '',
    "clientUpdatedAt" TIMESTAMP(3) NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "lesson_packages_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "lesson_packages_userId_updatedAt_idx" ON "lesson_packages"("userId", "updatedAt");

-- CreateIndex
CREATE UNIQUE INDEX "lesson_packages_userId_clientId_key" ON "lesson_packages"("userId", "clientId");

-- AddForeignKey
ALTER TABLE "lesson_packages" ADD CONSTRAINT "lesson_packages_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
