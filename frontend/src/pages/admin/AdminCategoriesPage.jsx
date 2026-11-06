import { useState } from 'react';
import { Link } from 'react-router';
import toast from 'react-hot-toast';
import { adminApi, catalogApi } from '../../api/endpoints';
import { Table, Td } from '../../components/admin/Table';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { ErrorState } from '../../components/ui/ErrorState';
import { PageHeader } from '../../components/ui/PageHeader';
import { Spinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useAsync } from '../../hooks/useAsync';
import { getErrorMessage, getFieldErrors } from '../../utils/errors';

export default function AdminCategoriesPage() {
  const { data, loading, error, reload } = useAsync(() => catalogApi.categories(), []);
  const [values, setValues] = useState({ name: '', description: '' });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!values.name.trim()) {
      setErrors({ name: 'Name is required' });
      return;
    }
    setSaving(true);
    try {
      const created = await adminApi.createCategory({ name: values.name.trim(), description: values.description.trim() || null });
      toast.success(`Category "${created.name}" created`);
      setValues({ name: '', description: '' });
      setErrors({});
      reload();
    } catch (err) {
      setErrors(getFieldErrors(err));
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader title="Categories" />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div>
          {loading ? <Spinner /> : error ? <ErrorState error={error} onRetry={reload} /> : (
            <Table head={['Name', 'Slug', { label: 'Active products', align: 'right' }, '']} caption="Categories">
              {data.map((c) => (
                <tr key={c.id}>
                  <Td><span className="font-medium text-gray-900">{c.name}</span>
                    {c.description && <p className="max-w-sm truncate text-xs text-gray-500">{c.description}</p>}</Td>
                  <Td className="text-gray-500">{c.slug}</Td>
                  <Td align="right">{c.productCount}</Td>
                  <Td><Link to={`/admin/products?category=${c.slug}`} className="text-brand-700 hover:underline">View products</Link></Td>
                </tr>
              ))}
            </Table>
          )}
        </div>
        <Card className="h-fit">
          <h2 className="mb-4 font-semibold text-gray-900">New category</h2>
          <form onSubmit={submit} noValidate className="space-y-4">
            <TextField label="Name" value={values.name} onChange={(e) => setValues({ ...values, name: e.target.value })}
                       error={errors.name} hint="The URL slug is generated from the name" />
            <TextField label="Description (optional)" value={values.description}
                       onChange={(e) => setValues({ ...values, description: e.target.value })} error={errors.description} />
            <Button type="submit" loading={saving} className="w-full">Create category</Button>
          </form>
        </Card>
      </div>
    </>
  );
}
