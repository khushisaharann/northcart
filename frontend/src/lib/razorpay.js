/**
 * Loads Razorpay's checkout.js once, on demand (only customers who pay need it).
 * The popup receives only the PUBLIC key id; the secret stays on the server.
 */
const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';
let loading = null;

export function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = SCRIPT_URL;
      script.async = true;
      script.onload = () => resolve(window.Razorpay);
      script.onerror = () => {
        loading = null;
        reject(new Error('Could not load the payment window. Check your connection.'));
      };
      document.body.appendChild(script);
    });
  }
  return loading;
}

/**
 * Opens the Razorpay popup. Resolves with {providerOrderId, providerPaymentId, signature}
 * on success; rejects with {cancelled, message} when the customer closes the popup
 * (message = the last failure reason, if an attempt failed).
 */
export async function openRazorpayCheckout(init) {
  const Razorpay = await loadRazorpay();
  return new Promise((resolve, reject) => {
    // A failed attempt (declined card) keeps the popup open so the customer can retry;
    // we only give up when they close it.
    let lastError = null;
    const checkout = new Razorpay({
      key: init.keyId,
      order_id: init.providerOrderId,
      amount: init.amount,
      currency: init.currency,
      name: 'NorthCart',
      description: `Order ${init.orderNumber}`,
      prefill: { name: init.customerName, email: init.customerEmail, contact: init.customerPhone ?? '' },
      theme: { color: '#4f46e5' },
      handler: (response) =>
        resolve({
          providerOrderId: response.razorpay_order_id,
          providerPaymentId: response.razorpay_payment_id,
          signature: response.razorpay_signature,
        }),
      modal: { ondismiss: () => reject({ cancelled: !lastError, message: lastError }) },
    });
    checkout.on('payment.failed', (response) => {
      lastError = response.error?.description || 'Payment failed';
    });
    checkout.open();
  });
}
