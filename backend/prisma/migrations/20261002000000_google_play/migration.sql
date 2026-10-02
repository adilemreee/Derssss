-- Android: Google ile giriş ve Google Play satın almaları.
-- Yalnızca ekleme ve gevşetme: yayındaki iOS uygulaması etkilenmez.

-- Google ile açılan hesabın Apple kimliği yoktur.
ALTER TABLE "users" ALTER COLUMN "appleSub" DROP NOT NULL;
ALTER TABLE "users" ADD COLUMN "googleSub" TEXT;
CREATE UNIQUE INDEX "users_googleSub_key" ON "users"("googleSub");

-- Aboneliğin hangi mağazadan geldiği ve Play'e en son ne zaman sorulduğu.
ALTER TABLE "subscriptions" ADD COLUMN "store" TEXT NOT NULL DEFAULT 'app_store';
ALTER TABLE "subscriptions" ADD COLUMN "lastCheckedAt" TIMESTAMP(3);
