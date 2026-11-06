"""Regenerates postman/*.json. Edit the requests here, then run: python3 scripts/generate-postman.py"""
import json, uuid, pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / 'postman'
OUT.mkdir(exist_ok=True)

def req(name, method, path, body=None, auth=None, tests=None, desc='', headers=None, query=None, prereq=None):
    h = [{'key': 'Content-Type', 'value': 'application/json'}] if body is not None else []
    if headers:
        h += [{'key': k, 'value': v} for k, v in headers.items()]
    raw_path = path + ('?' + '&'.join(f'{k}={v}' for k, v in query.items()) if query else '')
    item = {
        'name': name,
        'request': {
            'method': method,
            'header': h,
            'url': {
                'raw': '{{baseUrl}}' + raw_path,
                'host': ['{{baseUrl}}'],
                'path': [p for p in path.strip('/').split('/')],
                **({'query': [{'key': k, 'value': v} for k, v in query.items()]} if query else {}),
            },
            'description': desc,
        },
    }
    if auth:
        item['request']['auth'] = {'type': 'bearer', 'bearer': [{'key': 'token', 'value': '{{' + auth + '}}', 'type': 'string'}]}
    else:
        item['request']['auth'] = {'type': 'noauth'}
    if body is not None:
        raw = json.dumps(body, indent=2)
        for numeric in ('categoryId', 'productId', 'addressId'):   # ids must be JSON numbers, not strings
            raw = raw.replace('"{{%s}}"' % numeric, '{{%s}}' % numeric)
        item['request']['body'] = {'mode': 'raw', 'raw': raw, 'options': {'raw': {'language': 'json'}}}
    events = []
    if prereq:
        events.append({'listen': 'prerequest', 'script': {'type': 'text/javascript', 'exec': prereq}})
    if tests:
        events.append({'listen': 'test', 'script': {'type': 'text/javascript', 'exec': tests}})
    if events:
        item['event'] = events
    return item

def status(code, extra=()):
    codes = code if isinstance(code, list) else [code]
    return [f"pm.test('HTTP {' or '.join(map(str, codes))}', () => pm.expect(pm.response.code).to.be.oneOf({codes}));",
            "pm.test('has X-Request-Id', () => pm.expect(pm.response.headers.get('X-Request-Id')).to.be.ok);",
            *extra]

J = 'const body = pm.response.json();'

auth = {'name': '1. Auth', 'item': [
    req('Register customer', 'POST', '/api/v1/auth/register',
        {'fullName': 'Khushi Kumar', 'email': '{{customerEmail}}', 'password': '{{customerPassword}}', 'phone': '9876543210'},
        prereq=["// a fresh email every run, so Register never collides",
                "pm.collectionVariables.set('customerEmail', `khushi.${Date.now()}@example.com`);"],
        tests=status(201, [J, "pm.collectionVariables.set('customerToken', body.accessToken);",
                           "pm.test('role CUSTOMER', () => pm.expect(body.user.roles).to.include('CUSTOMER'));"]),
        desc='Creates a CUSTOMER and returns a JWT. The test script stores it in {{customerToken}}.'),
    req('Login customer', 'POST', '/api/v1/auth/login',
        {'email': '{{customerEmail}}', 'password': '{{customerPassword}}'},
        tests=status(200, [J, "pm.collectionVariables.set('customerToken', body.accessToken);"])),
    req('Login admin', 'POST', '/api/v1/auth/login',
        {'email': '{{adminEmail}}', 'password': '{{adminPassword}}'},
        tests=status(200, [J, "pm.collectionVariables.set('adminToken', body.accessToken);",
                           "pm.test('role ADMIN', () => pm.expect(body.user.roles).to.include('ADMIN'));"]),
        desc='Admin credentials come from the environment (dev defaults: application-dev.yml).'),
    req('Forgot password (always 200)', 'POST', '/api/v1/auth/forgot-password', {'email': '{{customerEmail}}'},
        tests=status(200), desc='Same response whether or not the email exists (no account enumeration). Check Mailpit at http://localhost:8025 for the link.'),
    req('Login with wrong password → 401', 'POST', '/api/v1/auth/login',
        {'email': '{{customerEmail}}', 'password': 'wrong-password-1'},
        tests=status(401, [J, "pm.test('ApiError format', () => pm.expect(body).to.include.keys('code', 'message', 'requestId'));"])),
]}

admin_setup = {'name': '2. Admin: catalog setup', 'item': [
    req('Create category', 'POST', '/api/v1/admin/categories',
        {'name': 'Postman {{$timestamp}}', 'description': 'Created from Postman'}, auth='adminToken',
        tests=status(201, [J, "pm.collectionVariables.set('categoryId', body.id);", "pm.collectionVariables.set('categorySlug', body.slug);"])),
    req('Create product (stock 5)', 'POST', '/api/v1/admin/products',
        {'name': 'Postman Lamp {{$timestamp}}', 'sku': 'pm-{{$timestamp}}', 'description': 'A warm desk lamp.',
         'price': 1250.00, 'categoryId': '{{categoryId}}', 'initialStock': 5, 'lowStockThreshold': 2, 'active': True,
         'images': [{'url': 'https://picsum.photos/seed/northcart/600/600', 'altText': 'Desk lamp'}]},
        auth='adminToken',
        tests=status(201, [J, "pm.collectionVariables.set('productId', body.id);", "pm.collectionVariables.set('productSlug', body.slug);",
                           "pm.test('SKU upper-cased', () => pm.expect(body.sku).to.eql(body.sku.toUpperCase()));"])),
    req('List products (admin, incl. inactive)', 'GET', '/api/v1/admin/products', auth='adminToken',
        query={'page': '0', 'size': '20'}, tests=status(200)),
    req('Get product (admin view)', 'GET', '/api/v1/admin/products/{{productId}}', auth='adminToken',
        tests=status(200, [J, "pm.test('stock 5', () => pm.expect(body.availableQuantity).to.eql(5));"])),
    req('Adjust stock +10 (restock)', 'PATCH', '/api/v1/admin/inventory/{{productId}}',
        {'delta': 10, 'reason': 'Postman restock'}, auth='adminToken', tests=status(200)),
    req('Adjust stock -10 (back to 5)', 'PATCH', '/api/v1/admin/inventory/{{productId}}',
        {'delta': -10, 'reason': 'Postman undo'}, auth='adminToken',
        tests=status(200, [J, "pm.test('stock 5', () => pm.expect(body.availableQuantity).to.eql(5));"])),
]}

catalog = {'name': '3. Storefront (public)', 'item': [
    req('Categories', 'GET', '/api/v1/categories', tests=status(200)),
    req('Search products', 'GET', '/api/v1/products',
        query={'q': 'postman lam', 'category': '{{categorySlug}}', 'sort': 'relevance', 'page': '0', 'size': '12'},
        tests=status(200, [J, "pm.test('finds our product', () => pm.expect(body.content.map(p => p.id)).to.include(pm.collectionVariables.get('productId') * 1));"]),
        desc='Other params: minPrice, maxPrice, inStock=true, sort=newest|price_asc|price_desc|popular|relevance'),
    req('Product by slug', 'GET', '/api/v1/products/{{productSlug}}', tests=status(200)),
    req('Unknown product → 404', 'GET', '/api/v1/products/no-such-product', tests=status(404)),
]}

customer = {'name': '4. Customer: profile & cart', 'item': [
    req('My profile', 'GET', '/api/v1/users/me', auth='customerToken', tests=status(200)),
    req('Update profile', 'PUT', '/api/v1/users/me', {'fullName': 'Khushi Kumar', 'phone': '9876500000'}, auth='customerToken', tests=status(200)),
    req('Add address', 'POST', '/api/v1/users/me/addresses',
        {'fullName': 'Khushi Kumar', 'phone': '9876543210', 'line1': '12 MG Road', 'line2': 'Near Metro', 'city': 'Bengaluru',
         'state': 'Karnataka', 'postalCode': '560001', 'country': 'India', 'makeDefault': True},
        auth='customerToken', tests=status(201, [J, "pm.collectionVariables.set('addressId', body.id);"])),
    req('Add 2 to cart', 'POST', '/api/v1/cart/items', {'productId': '{{productId}}', 'quantity': 2}, auth='customerToken',
        tests=status(200, [J, "pm.collectionVariables.set('cartItemId', body.items[0].itemId);"])),
    req('Change quantity to 3', 'PATCH', '/api/v1/cart/items/{{cartItemId}}', {'quantity': 3}, auth='customerToken', tests=status(200)),
    req('Change quantity back to 2', 'PATCH', '/api/v1/cart/items/{{cartItemId}}', {'quantity': 2}, auth='customerToken',
        tests=status(200, [J, "pm.test('free shipping over ₹999', () => pm.expect(Number(body.shippingFee)).to.eql(0));"])),
    req('Too many → 409 INSUFFICIENT_STOCK', 'POST', '/api/v1/cart/items', {'productId': '{{productId}}', 'quantity': 50}, auth='customerToken',
        tests=status(409)),
    req('View cart', 'GET', '/api/v1/cart', auth='customerToken',
        tests=status(200, [J, "pm.test('checkout ready', () => pm.expect(body.checkoutReady).to.be.true);"])),
]}

orders = {'name': '5. Checkout & payment', 'item': [
    req('Checkout', 'POST', '/api/v1/orders', {'addressId': '{{addressId}}'}, auth='customerToken',
        headers={'Idempotency-Key': '{{idempotencyKey}}'},
        prereq=["pm.collectionVariables.set('idempotencyKey', pm.variables.replaceIn('{{$guid}}'));"],
        tests=status(201, [J, "pm.collectionVariables.set('orderNumber', body.orderNumber);",
                           "pm.test('PLACED', () => pm.expect(body.status).to.eql('PLACED'));"]),
        desc='Idempotency-Key: a new UUID per checkout attempt. Re-sending the same key returns the same order (200), never a second one.'),
    req('Checkout again, same key → same order (200)', 'POST', '/api/v1/orders', {'addressId': '{{addressId}}'}, auth='customerToken',
        headers={'Idempotency-Key': '{{idempotencyKey}}'},
        tests=status(200, [J, "pm.test('same order', () => pm.expect(body.orderNumber).to.eql(pm.collectionVariables.get('orderNumber')));"])),
    req('Initiate payment', 'POST', '/api/v1/payments/{{orderNumber}}/initiate', auth='customerToken',
        tests=status(200, [J, "pm.collectionVariables.set('providerOrderId', body.providerOrderId);",
                           "pm.test('amount in paise', () => pm.expect(body.amount).to.eql(250000));"])),
    req('Fake gateway: pay', 'POST', '/api/v1/payments/fake/pay', {'providerOrderId': '{{providerOrderId}}'}, auth='customerToken',
        tests=status(200, [J, "pm.collectionVariables.set('providerPaymentId', body.providerPaymentId);",
                           "pm.collectionVariables.set('signature', body.signature);"]),
        desc='Only exists with PAYMENT_PROVIDER=fake (the dev default). Plays the role of the Razorpay popup.'),
    req('Verify with forged signature → 400', 'POST', '/api/v1/payments/verify',
        {'providerOrderId': '{{providerOrderId}}', 'providerPaymentId': '{{providerPaymentId}}', 'signature': '0' * 64},
        auth='customerToken', tests=status(400)),
    req('Verify payment', 'POST', '/api/v1/payments/verify',
        {'providerOrderId': '{{providerOrderId}}', 'providerPaymentId': '{{providerPaymentId}}', 'signature': '{{signature}}'},
        auth='customerToken', tests=status(200, [J, "pm.test('CONFIRMED', () => pm.expect(body.status).to.eql('CONFIRMED'));"])),
    req('My orders', 'GET', '/api/v1/orders', auth='customerToken', query={'page': '0', 'size': '10'}, tests=status(200)),
    req('My order detail', 'GET', '/api/v1/orders/{{orderNumber}}', auth='customerToken', tests=status(200)),
]}

admin_ops = {'name': '6. Admin: operations', 'item': [
    req('Dashboard (30 days)', 'GET', '/api/v1/admin/dashboard', auth='adminToken', query={'days': '30'}, tests=status(200)),
    req('Search orders', 'GET', '/api/v1/admin/orders', auth='adminToken', query={'status': 'CONFIRMED', 'q': '{{orderNumber}}'},
        tests=status(200, [J, "pm.test('found', () => pm.expect(body.totalElements).to.eql(1));"])),
    req('Order detail (admin)', 'GET', '/api/v1/admin/orders/{{orderNumber}}', auth='adminToken',
        tests=status(200, [J, "pm.test('can ship or cancel', () => pm.expect(body.allowedTransitions).to.eql(['SHIPPED', 'CANCELLED']));"])),
    req('Mark SHIPPED', 'PATCH', '/api/v1/admin/orders/{{orderNumber}}/status', {'status': 'SHIPPED'}, auth='adminToken', tests=status(200)),
    req('Mark DELIVERED', 'PATCH', '/api/v1/admin/orders/{{orderNumber}}/status', {'status': 'DELIVERED'}, auth='adminToken', tests=status(200)),
    req('Illegal transition → 409', 'PATCH', '/api/v1/admin/orders/{{orderNumber}}/status', {'status': 'SHIPPED'}, auth='adminToken', tests=status(409)),
    req('Search customers', 'GET', '/api/v1/admin/customers', auth='adminToken', query={'q': '{{customerEmail}}'},
        tests=status(200, [J, "pm.collectionVariables.set('customerId', body.content[0].id);"])),
    req('Disable customer', 'PATCH', '/api/v1/admin/customers/{{customerId}}/status', {'enabled': False}, auth='adminToken', tests=status(200)),
    req('Disabled customer token → 401', 'GET', '/api/v1/cart', auth='customerToken', tests=status(401)),
    req('Re-enable customer', 'PATCH', '/api/v1/admin/customers/{{customerId}}/status', {'enabled': True}, auth='adminToken', tests=status(200)),
    req('Customer on admin API → 403', 'GET', '/api/v1/admin/dashboard', auth='customerToken', tests=status(403)),
]}

cancel = {'name': '7. Cancel flow', 'item': [
    req('Add 1 to cart', 'POST', '/api/v1/cart/items', {'productId': '{{productId}}', 'quantity': 1}, auth='customerToken', tests=status(200)),
    req('Checkout (no key)', 'POST', '/api/v1/orders', {'addressId': '{{addressId}}'}, auth='customerToken',
        tests=status(201, [J, "pm.collectionVariables.set('orderNumber2', body.orderNumber);"])),
    req('Cancel unpaid order', 'PATCH', '/api/v1/orders/{{orderNumber2}}/cancel', auth='customerToken',
        tests=status(200, [J, "pm.test('CANCELLED', () => pm.expect(body.status).to.eql('CANCELLED'));"])),
    req('Stock restored (3 left)', 'GET', '/api/v1/admin/products/{{productId}}', auth='adminToken',
        tests=status(200, [J, "pm.test('stock 3', () => pm.expect(body.availableQuantity).to.eql(3));"])),
]}

variables = ['customerEmail', 'customerToken', 'adminToken', 'categoryId', 'categorySlug', 'productId', 'productSlug',
             'addressId', 'cartItemId', 'idempotencyKey', 'orderNumber', 'orderNumber2', 'providerOrderId',
             'providerPaymentId', 'signature', 'customerId']

collection = {
    'info': {
        '_postman_id': str(uuid.uuid5(uuid.NAMESPACE_URL, 'northcart-collection')),
        'name': 'NorthCart API',
        'description': ('The whole NorthCart API in journey order. Import together with NorthCart.local.postman_environment.json, '
                        'select the "NorthCart (local)" environment, then Run collection: every request stores what the next one needs '
                        '(tokens, ids, order number) in collection variables. Needs PAYMENT_PROVIDER=fake.\n\n'
                        'Interactive docs: {{baseUrl}}/swagger-ui.html'),
        'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    },
    'item': [auth, admin_setup, catalog, customer, orders, admin_ops, cancel],
    'variable': [{'key': k, 'value': ''} for k in variables] + [{'key': 'customerPassword', 'value': 'Khushi12345'}],
}

env = {
    'id': str(uuid.uuid5(uuid.NAMESPACE_URL, 'northcart-env-local')),
    'name': 'NorthCart (local)',
    'values': [
        {'key': 'baseUrl', 'value': 'http://localhost:8080', 'enabled': True},
        {'key': 'adminEmail', 'value': 'admin@northcart.local', 'enabled': True},
        {'key': 'adminPassword', 'value': 'Admin12345', 'type': 'secret', 'enabled': True},
    ],
    '_postman_variable_scope': 'environment',
}

(OUT / 'NorthCart.postman_collection.json').write_text(json.dumps(collection, indent=2, ensure_ascii=False) + '\n')
(OUT / 'NorthCart.local.postman_environment.json').write_text(json.dumps(env, indent=2) + '\n')
n = sum(len(f['item']) for f in collection['item'])
print('requests:', n)
