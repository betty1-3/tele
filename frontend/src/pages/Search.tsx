import React, { FormEvent, useEffect, useState } from 'react';
import { RefreshCwIcon, SearchIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Panel } from '../components/ui/Panel';
import { EmptyState, ErrorState } from '../components/ui/StateBlock';
import { ApiError } from '../utils/api/client';
import { searchApi, SearchAnalytics, SearchResponse, SearchStatus } from '../utils/api/search';

const label = (value: string) => value.replace(/_/g, ' ').replace(/\b\w/g, (c: string) => c.toUpperCase());

function SummaryGroup({ title, items }: { title: string; items: Array<{ label: string; count: number }> }) {
  return <div><h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</h3>
    {items.length ? <ul className="space-y-2">{items.slice(0, 6).map((item) => <li key={item.label} className="flex justify-between gap-3 text-sm"><span className="truncate text-ink">{label(item.label)}</span><span className="tabular text-ink-muted">{item.count}</span></li>)}</ul> : <p className="text-xs text-ink-muted">No indexed values yet</p>}
  </div>;
}

function Highlight({ value }: { value: string }) {
  const chunks = value.split(/(<em>|<\/em>)/gi);
  let active = false;
  return <>{chunks.filter(Boolean).map((chunk, i) => { if (chunk.toLowerCase() === '<em>') { active = true; return null; } if (chunk.toLowerCase() === '</em>') { active = false; return null; } return active ? <mark key={i} className="rounded bg-amber-100 px-0.5 text-inherit">{chunk}</mark> : <React.Fragment key={i}>{chunk}</React.Fragment>; })}</>;
}

export function SearchPage() {
  const [form, setForm] = useState({ q: '', type: '', city: '', category: '', minExperience: '', minRating: '' });
  const [request, setRequest] = useState({ q: '', type: '', city: '', category: '', minExperience: '', minRating: '', page: 0 });
  const [results, setResults] = useState<SearchResponse | null>(null);
  const [analytics, setAnalytics] = useState<SearchAnalytics | null>(null);
  const [status, setStatus] = useState<SearchStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [working, setWorking] = useState(false);

  useEffect(() => { searchApi.status().then(setStatus).catch(() => setStatus({ state: 'offline', connected: false, index: 'telemedical-catalog-v1' })); searchApi.analytics().then(setAnalytics).catch(() => setAnalytics(null)); }, []);
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    const values: Record<string, string | number | undefined> = { ...request, size: 10, minExperience: request.minExperience || undefined, minRating: request.minRating || undefined };
    searchApi.search(values).then((data) => { if (active) setResults(data); }).catch((err: unknown) => { if (active) { setError(err instanceof ApiError && err.status === 0 ? 'Backend is offline. Start the backend, then retry.' : err instanceof ApiError && err.status === 503 ? 'Elasticsearch is offline or unavailable. Start the cluster, then retry.' : err instanceof Error ? err.message : 'Search failed.'); setResults(null); } }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [request]);

  const submit = (event: FormEvent) => { event.preventDefault(); setRequest({ ...form, page: 0 }); };
  const reindex = async () => { setWorking(true); try { const next = await searchApi.reindex(); setStatus(next); setAnalytics(await searchApi.analytics()); toast.success(`Indexed ${next.documents ?? 0} catalog records`); setRequest((current) => ({ ...current })); } catch { toast.error('Catalog indexing failed. Check the backend and Elasticsearch logs.'); } finally { setWorking(false); } };
  const pageCount = results ? Math.max(1, Math.ceil(results.total / results.size)) : 1;

  return <div className="space-y-5">
    <Panel title="Catalog search" description="Search doctors, hospitals, medicines and diagnostic tests" bodyPadding>
      <form onSubmit={submit} className="grid grid-cols-1 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <label className="md:col-span-2 xl:col-span-3"><span className="mb-1 block text-xs font-medium text-slate-600">Search terms</span><div className="flex rounded-md border border-line-strong bg-white"><SearchIcon className="ml-3 mt-2.5 h-4 w-4 text-slate-400"/><input aria-label="Search terms" className="min-w-0 flex-1 rounded-md px-3 py-2 text-sm outline-none" placeholder="Name, specialty, city..." value={form.q} onChange={(e) => setForm({ ...form, q: e.target.value })}/></div></label>
        <label><span className="mb-1 block text-xs font-medium text-slate-600">Record type</span><select className="w-full rounded-md border border-line-strong bg-white px-3 py-2 text-sm" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}><option value="">All types</option>{['doctor','hospital','medicine','test'].map((v) => <option key={v} value={v}>{label(v)}</option>)}</select></label>
        <label><span className="mb-1 block text-xs font-medium text-slate-600">City</span><input className="w-full rounded-md border border-line-strong px-3 py-2 text-sm" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} placeholder="Exact city" /></label>
        <label><span className="mb-1 block text-xs font-medium text-slate-600">Category</span><input className="w-full rounded-md border border-line-strong px-3 py-2 text-sm" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} placeholder="Specialty or type" /></label>
        <label><span className="mb-1 block text-xs font-medium text-slate-600">Min. experience</span><input type="number" min="0" className="w-full rounded-md border border-line-strong px-3 py-2 text-sm" value={form.minExperience} onChange={(e) => setForm({ ...form, minExperience: e.target.value })} /></label>
        <label><span className="mb-1 block text-xs font-medium text-slate-600">Min. rating</span><input type="number" min="1" max="5" className="w-full rounded-md border border-line-strong px-3 py-2 text-sm" value={form.minRating} onChange={(e) => setForm({ ...form, minRating: e.target.value })} /></label>
        <div className="flex items-end"><button className="w-full rounded-md bg-sky-700 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-800">Search catalog</button></div>
      </form>
    </Panel>

    <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(260px,1fr)]">
      <Panel title="Search results" description={results ? `${results.total} matching records` : 'Ranked by relevance'}>
        {loading ? <p className="p-8 text-center text-sm text-ink-muted">Searching indexed records…</p> : error ? <ErrorState compact message={error} onRetry={() => setRequest((current) => ({ ...current }))} /> : results?.results.length ? <>
          <ul className="divide-y divide-line">{results.results.map((item) => <li key={`${item.entityType}-${item.entityId}`} className="px-4 py-3.5"><div className="flex flex-wrap items-start justify-between gap-2"><div><h3 className="text-sm font-semibold text-ink">{item.highlights?.name?.[0] ? <Highlight value={item.highlights.name[0]}/> : item.name}</h3><p className="mt-1 text-xs text-ink-muted">{label(item.entityType)}{item.category ? ` · ${label(item.category)}` : ''}{item.city ? ` · ${item.city}` : ''}{item.area ? ` · ${item.area}` : ''}</p>{item.experience !== undefined || item.rating !== undefined ? <p className="mt-1 text-xs text-slate-600">{item.experience !== undefined ? `${item.experience} years experience` : ''}{item.rating !== undefined ? ` · ${item.rating}/5 rating` : ''}</p> : null}<p className="mt-1 text-sm text-slate-600">{item.highlights?.description?.[0] ? <Highlight value={item.highlights.description[0]}/> : ''}</p></div><span className="text-2xs text-slate-400">ID {item.entityId}</span></div></li>)}</ul>
          <div className="flex items-center justify-between border-t border-line px-4 py-3 text-xs text-ink-muted"><span>Page {results.page + 1} of {pageCount}</span><div className="flex gap-2"><button disabled={results.page === 0} onClick={() => setRequest((r) => ({ ...r, page: r.page - 1 }))} className="rounded border border-line-strong px-3 py-1.5 disabled:opacity-40">Previous</button><button disabled={results.page + 1 >= pageCount} onClick={() => setRequest((r) => ({ ...r, page: r.page + 1 }))} className="rounded border border-line-strong px-3 py-1.5 disabled:opacity-40">Next</button></div></div>
        </> : <EmptyState compact title="No catalog results" hint="Try a broader term or remove one of the filters."/>}
      </Panel>

      <div className="space-y-4">
        <Panel title="Index status" description="Oracle remains the source of truth" bodyPadding>
          <div className="flex items-center justify-between"><span className={`text-sm font-semibold ${status?.connected ? 'text-emerald-700' : 'text-rose-700'}`}>{status?.connected ? 'Connected' : 'Offline'}</span><button disabled={working} onClick={reindex} className="inline-flex items-center gap-1.5 rounded border border-line-strong px-2.5 py-1.5 text-xs font-medium disabled:opacity-50"><RefreshCwIcon size={13} className={working ? 'animate-spin' : ''}/>Rebuild index</button></div>
          <p className="mt-2 break-all text-xs text-ink-muted">{status?.index ?? 'telemedical-catalog-v1'}</p><p className="mt-1 text-xs text-ink-muted">{status?.documents !== undefined ? `${status.documents} indexed records` : 'Index has not reported a successful sync'}</p><p className="mt-1 text-xs text-ink-muted">{status?.lastSync ? `Last sync ${new Date(status.lastSync).toLocaleString()}` : 'Sync runs periodically and can be triggered manually.'}</p>
        </Panel>
        <Panel title="Indexed catalog analytics" description="Current Elasticsearch index totals" bodyPadding>
          {!analytics ? <p className="text-xs text-ink-muted">Analytics are unavailable while Elasticsearch is offline or before the first index is created.</p> : <div className="space-y-4"><div className="grid grid-cols-2 gap-3"><div className="rounded-md bg-slate-50 p-3"><p className="text-xs text-slate-500">Records</p><p className="mt-1 text-xl font-bold tabular">{analytics.total}</p></div><div className="rounded-md bg-slate-50 p-3"><p className="text-xs text-slate-500">Avg. doctor experience</p><p className="mt-1 text-xl font-bold tabular">{analytics.averageExperience?.toFixed(1) ?? '—'} yrs</p></div><div className="rounded-md bg-slate-50 p-3"><p className="text-xs text-slate-500">Avg. doctor rating</p><p className="mt-1 text-xl font-bold tabular">{analytics.averageRating?.toFixed(1) ?? '—'} / 5</p></div></div><SummaryGroup title="Records by type" items={analytics.byType}/><SummaryGroup title="Popular cities" items={analytics.byCity}/><SummaryGroup title="Catalog categories" items={analytics.byCategory}/></div>}
        </Panel>
      </div>
    </div>
  </div>;
}
