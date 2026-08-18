import Redis from 'ioredis';
import { config, REDIS_PREFIX } from '../config.js';

export const redis = new Redis(config.REDIS_URL, {
  keyPrefix: REDIS_PREFIX,
  maxRetriesPerRequest: 3,
  lazyConnect: false,
});

redis.on('error', (err) => {
  console.error('[redis]', err.message);
});
