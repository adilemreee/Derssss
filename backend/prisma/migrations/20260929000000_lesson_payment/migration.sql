-- Toplu ödeme: dersin hangi ödemeyle ödendiği. Yalnız ekleme.

-- AlterTable
ALTER TABLE "lessons" ADD COLUMN "paymentClientId" UUID;
