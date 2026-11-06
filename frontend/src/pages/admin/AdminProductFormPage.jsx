import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import toast from 'react-hot-toast';
import { ArrowDown, ArrowLeft, ArrowUp, Plus, Trash2 } from 'lucide-react';
import { adminApi, catalogApi } from '../../api/endpoints';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { ErrorState } from '../../components/ui/ErrorState';
import { ProductImage } from '../../components/ui/ProductImage';
import { Spinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useAsync } from '../../hooks/useAsync';
import { getErrorMessage, getFieldErrors } from '../../utils/errors';

const EMPTY = { name: '', sku: '', description: '', price: '', categoryId: '', initialStock: '0', lowStockThreshold: '5', active: true, images: [] };

function validateProduct(v, isNew) {
  const e = {};
  if (!v.name.trim()) e.name = 'Name is required';
  if (isNew && !/^[A-Za-z0-9-]+$/.test(v.sku.trim())) e.sku = 'Letters, digits and - only';
  if (!(Number(v.price) > 0)) e.price = 'Enter a price above 0';
  else if (!/^\d+(\.\d{1,2})?$/.test(String(v.price))) e.price = 'At most 2 decimals';
  if (!v.categoryId) e.categoryId = 'Choose a category';
  if (isNew && !(Number(v.initialStock) >= 0)) e.initialStock = 'Stock must be 0 or more';
  if (!(Number(v.lowStockThreshold) >= 0)) e.lowStockThreshold = 'Must be 0 or more';
  v.images.forEach((img, i) => {
    if (!/^https:\/\/\S+$/.test(img.url.trim())) e[`images[${i}].url`] = 'Must be an https URL';
  });
  return e;
}

/** Create (/admin/products/new) and edit (/admin/products/:id/edit) share this form. */
export default function AdminProductFormPage() {
  const { id } = useParams();
  const isNew = !id;
  const navigate = useNavigate();
  const categories = useAsync(() => catalogApi.categories(), []);
  const existing = useAsync(() => (isNew ? Promise.resolve(null) : adminApi.product(id)), [id]);
  const [values, setValues] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const p = existing.data;
    if (!p) return;
    setValues({
      name: p.name, sku: p.sku, description: p.description ?? '', price: String(p.price), categoryId: String(p.category.id),
      initialStock: '0', lowStockThreshold: String(p.lowStockThreshold), active: p.active,
      images: p.images.map((img) => ({ url: img.url, altText: img.altText ?? '' })),
    });
  }, [existing.data]);

  if (existing.loading || categories.loading) return <Spinner />;
  if (existing.error) return <ErrorState error={existing.error} onRetry={existing.reload} />;

  const set = (field) => (e) => setValues({ ...values, [field]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const setImage = (i, field, value) => setValues({ ...values, images: values.images.map((img, j) => (j === i ? { ...img, [field]: value } : img)) });
  const moveImage = (i, dir) => {
    const images = [...values.images];
    [images[i], images[i + dir]] = [images[i + dir], images[i]];
    setValues({ ...values, images });
  };

  const submit = async (e) => {
    e.preventDefault();
    const clientErrors = validateProduct(values, isNew);
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length) return;
    const common = {
      name: values.name.trim(), description: values.description.trim() || null, price: values.price,
      categoryId: Number(values.categoryId), lowStockThreshold: Number(values.lowStockThreshold), active: values.active,
      images: values.images.map((img) => ({ url: img.url.trim(), altText: img.altText.trim() || null })),
    };
    setSaving(true);
    try {
      if (isNew) {
        const created = await adminApi.createProduct({ ...common, sku: values.sku.trim(), initialStock: Number(values.initialStock) });
        toast.success(`Created ${created.name}`);
        navigate(`/admin/products/${created.id}/edit`, { replace: true });
      } else {
        await adminApi.updateProduct(id, common);
        toast.success('Product saved');
        existing.reload();
      }
    } catch (err) {
      setErrors(getFieldErrors(err));
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate>
      <Link to="/admin/products" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-900">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Products
      </Link>
      <div className="mb-6 mt-3 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold tracking-tight text-gray-900">{isNew ? 'New product' : values.name || 'Edit product'}</h1>
        <div className="flex gap-2">
          {!isNew && existing.data?.active && (
            <Link to={`/products/${existing.data.slug}`} className="inline-flex h-10 items-center rounded-lg px-4 text-sm font-medium ring-1 ring-gray-300 hover:bg-gray-50">
              View in shop
            </Link>
          )}
          <Button type="submit" loading={saving}>{isNew ? 'Create product' : 'Save changes'}</Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-6">
          <Card>
            <h2 className="mb-4 font-semibold text-gray-900">Details</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField className="sm:col-span-2" label="Name" value={values.name} onChange={set('name')} error={errors.name} />
              <TextField label="SKU" value={values.sku} onChange={set('sku')} error={errors.sku} disabled={!isNew}
                         hint={isNew ? 'Unique code, e.g. ELEC-010. Cannot be changed later.' : 'SKUs are permanent'} />
              <TextField label="Price (₹)" type="number" min="0.01" step="0.01" inputMode="decimal" value={values.price} onChange={set('price')} error={errors.price} />
              <div className="sm:col-span-2">
                <label htmlFor="description" className="mb-1 block text-sm font-medium text-gray-700">Description</label>
                <textarea id="description" rows={5} value={values.description} onChange={set('description')}
                          className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100" />
              </div>
            </div>
          </Card>

          <Card>
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-semibold text-gray-900">Images</h2>
              <Button type="button" variant="ghost" size="sm" disabled={values.images.length >= 10}
                      onClick={() => setValues({ ...values, images: [...values.images, { url: '', altText: '' }] })}>
                <Plus className="h-4 w-4" aria-hidden="true" /> Add image URL
              </Button>
            </div>
            {values.images.length === 0 && <p className="text-sm text-gray-500">No images. The first image is the thumbnail.</p>}
            <ul className="space-y-3">
              {values.images.map((img, i) => (
                <li key={i} className="flex gap-3 rounded-lg border border-gray-200 p-3">
                  <ProductImage src={img.url} alt="" className="h-16 w-16 shrink-0 rounded-md" />
                  <div className="grid flex-1 gap-2 sm:grid-cols-2">
                    <TextField label={i === 0 ? 'Image URL (thumbnail)' : 'Image URL'} value={img.url} placeholder="https://..."
                               onChange={(e) => setImage(i, 'url', e.target.value)} error={errors[`images[${i}].url`]} />
                    <TextField label="Alt text" value={img.altText} onChange={(e) => setImage(i, 'altText', e.target.value)} />
                  </div>
                  <div className="flex flex-col gap-1">
                    <button type="button" aria-label="Move up" disabled={i === 0} onClick={() => moveImage(i, -1)} className="rounded p-1 text-gray-500 hover:bg-gray-100 disabled:opacity-30"><ArrowUp className="h-4 w-4" /></button>
                    <button type="button" aria-label="Move down" disabled={i === values.images.length - 1} onClick={() => moveImage(i, 1)} className="rounded p-1 text-gray-500 hover:bg-gray-100 disabled:opacity-30"><ArrowDown className="h-4 w-4" /></button>
                    <button type="button" aria-label="Remove image" onClick={() => setValues({ ...values, images: values.images.filter((_, j) => j !== i) })} className="rounded p-1 text-gray-500 hover:bg-red-50 hover:text-red-600"><Trash2 className="h-4 w-4" /></button>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <h2 className="mb-4 font-semibold text-gray-900">Organisation</h2>
            <label htmlFor="category" className="mb-1 block text-sm font-medium text-gray-700">Category</label>
            <select id="category" value={values.categoryId} onChange={set('categoryId')} aria-invalid={Boolean(errors.categoryId)}
                    className="h-10 w-full rounded-lg border border-gray-300 bg-white px-3 text-sm">
              <option value="">Choose…</option>
              {(categories.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            {errors.categoryId && <p className="mt-1 text-xs text-red-600">{errors.categoryId}</p>}
            <label className="mt-4 flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" checked={values.active} onChange={set('active')} className="h-4 w-4 rounded border-gray-300" />
              Visible in the shop
            </label>
          </Card>
          <Card>
            <h2 className="mb-4 font-semibold text-gray-900">Inventory</h2>
            <div className="space-y-4">
              {isNew ? (
                <TextField label="Initial stock" type="number" min="0" value={values.initialStock} onChange={set('initialStock')} error={errors.initialStock} />
              ) : (
                <p className="text-sm text-gray-600">
                  In stock: <span className="font-semibold text-gray-900">{existing.data.availableQuantity}</span>.{' '}
                  <Link to="/admin/inventory" className="text-brand-700 hover:underline">Adjust stock</Link>
                </p>
              )}
              <TextField label="Low-stock alert at" type="number" min="0" value={values.lowStockThreshold}
                         onChange={set('lowStockThreshold')} error={errors.lowStockThreshold} hint="Shown as low stock at or below this number" />
            </div>
          </Card>
        </div>
      </div>
    </form>
  );
}
