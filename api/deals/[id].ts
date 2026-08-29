import type { IncomingMessage, ServerResponse } from "http";
import dealsHandler from "../deals";

export default async function handler(req: IncomingMessage, res: ServerResponse) {
  return dealsHandler(req as any, res as any);
}
