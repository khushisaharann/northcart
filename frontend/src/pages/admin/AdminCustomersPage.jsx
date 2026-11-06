import { useState } from 'react';
import { useSearchParams } from 'react-router';
import toast from 'react-hot-toast';
import { Users } from 'lucide-react';
import { adminApi } from '../../api/endpoints';
import { SearchInput } from '../../components/admin/SearchInput';
import { Table, Td } from '../../components/admin/Table';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Modal } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { Spinner } from '../../components/ui/Spinner';
import { useAsync } from '../../hooks/useAsync';
import { getErrorMessage } from '../../utils/errors';
import { formatDate, formatPrice } from '../../utils/format';

export default function AdminCustomersPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? 0);
  const { data, loading, error, reload, setData } = useAsync(() => adminApi.customers({ q: q || undefined, page, size: 20 }), [q, page]);
  const [pending, setPending] = useState(null); // customer awaiting "disable" confirmation
  const [busyId, setBusyId] = useState(null);

  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  };

  const setEnabled = async (customer, enabled) => {
    setPending(null);
    setBusyId(customer.id);
    try {
      const updated = await adminApi.setCustomerEnabled(customer.id, enabled);
      setData({ ...data, content: data.content.map((c) => (c.id === updated.id ? updated : c)) });
      toast.success(`${updated.fullName} ${enabled ? 'enabled' : 'disabled'}`);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <PageHeader title="Customers" />
      <div className="mb-4"><SearchInput value={q} onChange={(v) => update({ q: v })} placeholder="Name or email" label="Search customers" /></div>
      {loading ? <Spinner /> : error ? <ErrorState error={error} onRetry={reload} /> : data.content.length === 0 ? (
        <EmptyState icon={Users} title="No customers found" />
      ) : (
        <>
          <Table head={['Customer', 'Phone', { label: 'Orders', align: 'right' }, { label: 'Spent', align: 'right' }, 'Joined', 'Status', '']} caption="Customers">
            {data.content.map((c) => (
              <tr key={c.id} className={c.enabled ? '' : 'bg-gray-50'}>
                <Td><p className="font-medium text-gray-900">{c.fullName}</p><p className="text-xs text-gray-500">{c.email}</p></Td>
                <Td className="text-gray-600">{c.phone ?? '–'}</Td>
                <Td align="right">{c.orderCount}</Td>
                <Td align="right">{formatPrice(c.totalSpent)}</Td>
                <Td className="text-gray-500">{formatDate(c.memberSince)}</Td>
                <Td>{c.enabled
                  ? <span className="rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">Active</span>
                  : <span className="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">Disabled</span>}</Td>
                <Td>
                  <Button size="sm" variant={c.enabled ? 'danger' : 'secondary'} loading={busyId === c.id}
                          onClick={() => (c.enabled ? setPending(c) : setEnabled(c, true))}>
                    {c.enabled ? 'Disable' : 'Enable'}
                  </Button>
                </Td>
              </tr>
            ))}
          </Table>
          <Pagination page={page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />
        </>
      )}
      <Modal open={Boolean(pending)} title={`Disable ${pending?.fullName}?`} onClose={() => setPending(null)}>
        <p className="text-sm text-gray-600">They are logged out immediately and can't log in until you enable the account again. Their orders are kept.</p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setPending(null)}>Keep active</Button>
          <Button variant="danger" onClick={() => setEnabled(pending, false)}>Disable account</Button>
        </div>
      </Modal>
    </>
  );
}
