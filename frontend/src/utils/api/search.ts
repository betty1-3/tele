import { api } from './client';

export interface SearchResult { entityType: string; entityId: number; name: string; category?: string; city?: string; area?: string; experience?: number; rating?: number; score?: number; highlights?: Record<string, string[]> }
export interface SearchResponse { results: SearchResult[]; total: number; page: number; size: number }
export interface SearchAnalytics { total: number; byType: Array<{label:string;count:number}>; byCity: Array<{label:string;count:number}>; byCategory: Array<{label:string;count:number}>; averageExperience: number; averageRating: number }
export interface SearchStatus { state: string; connected: boolean; documents?: number; lastSync?: string; index: string; message?: string }

const params = (values: Record<string, string | number | undefined>) => {
  const query = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => { if (value !== undefined && value !== '') query.set(key, String(value)); });
  return query.toString();
};

export const searchApi = {
  search: (values: Record<string, string | number | undefined>) => api.get<SearchResponse>(`/search?${params(values)}`),
  analytics: () => api.get<SearchAnalytics>('/search/analytics'),
  status: () => api.get<SearchStatus>('/search/status'),
  reindex: () => api.post<SearchStatus>('/search/reindex', {})
};
