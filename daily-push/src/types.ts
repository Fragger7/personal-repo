/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export interface WorkoutDay {
  date: string; // YYYY-MM-DD
  p: [number | null, number | null, number | null]; // Pushups sets 1-3
  c: [number | null, number | null, number | null]; // Crunches sets 1-3
}

export interface GitHubConfig {
  username: string;
  pat: string;
  repo: string;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  message: string;
  isError?: boolean;
}
