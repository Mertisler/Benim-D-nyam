"use strict";

/**
 * Benim Dünyam Cloud Functions.
 *
 * - bdAiGatewayV1: yapay zekâ kapısı (ai/index.js).
 * - bdMediaCleanup*V2: doğrulanmış medya temizleme (mediaCleanupVerifiedV2.js).
 *   Fiziksel silme BD_CLEANUP_V2_ENABLED=true olmadıkça kilitlidir.
 */
const ai = require("./ai");
const cleanup = require("./mediaCleanupVerifiedV2");

exports.bdAiGatewayV1 = ai.bdAiGatewayV1;
exports.bdMediaCleanupStatusV2 = cleanup.bdMediaCleanupStatusV2;
exports.bdMediaCleanupPrepareV2 = cleanup.bdMediaCleanupPrepareV2;
exports.bdMediaCleanupExecuteV2 = cleanup.bdMediaCleanupExecuteV2;
