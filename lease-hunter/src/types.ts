export interface DeveloperFile {
  name: string;
  exists: boolean;
  size: number;
  mtime: string | null;
}

export interface SetupStatus {
  gitHubTokenConfigured: boolean;
  geminiApiKeyConfigured: boolean;
  appUrl: string;
  environment: string;
  time: string;
}

export interface TaxRuleResult {
  zipCode: string;
  taxType: string;
  defaultRate: number;
  showsTaxCredits: boolean;
  description: string;
}
