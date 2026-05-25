export type GradeItemSourceType = 'LAB' | 'HWK' | 'OTHER_COURSE_ITEM';

export interface GradeItem {
  id: number;
  courseId: number;
  name: string;
  sourceType: GradeItemSourceType;
  sourceId: number | null;
  fullScore: string;
  weight: string;
  includedInFinal: boolean;
  enabled: boolean;
  sortOrder: number;
}

export interface CreateGradeItemPayload {
  name: string;
  sourceType: GradeItemSourceType;
  sourceId: number | null;
  fullScore: string;
  weight: string;
  includedInFinal: boolean;
  sortOrder: number;
}

export interface UpdateGradeItemPayload extends CreateGradeItemPayload {
  enabled?: boolean;
}

export interface GradeRuleValidationResult {
  valid: boolean;
  totalIncludedWeight: string;
  errors: string[];
}
