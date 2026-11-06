import { useCallback, useState } from 'react';
import { paymentApi } from '../api/endpoints';
import { openRazorpayCheckout } from '../lib/razorpay';

/**
 * One payment flow for both gateways:
 *   1. initiate (server creates the gateway order)
 *   2. RAZORPAY: real popup   |   FAKE: our own test dialog (see FakePaymentDialog)
 *   3. verify (server checks the signature and confirms the order)
 *
 * pay() resolves with { status: 'success', order } or { status: 'failed' | 'cancelled', message }.
 * Server errors (e.g. 409 PAYMENT_LATE) are thrown to the caller.
 */
export function usePayment() {
  const [fakeCheckout, setFakeCheckout] = useState(null); // { init, resolve } while the test dialog is open
  const [processing, setProcessing] = useState(false);

  const collectPayment = useCallback((init) => {
    if (init.provider === 'RAZORPAY') {
      return openRazorpayCheckout(init);
    }
    // FAKE provider: show our dialog and wait for the user's choice.
    return new Promise((resolve, reject) => setFakeCheckout({ init, resolve, reject }));
  }, []);

  const pay = useCallback(
    async (orderNumber) => {
      setProcessing(true);
      try {
        const init = await paymentApi.initiate(orderNumber);
        let callback;
        try {
          callback = await collectPayment(init);
        } catch (outcome) {
          return outcome?.message
            ? { status: 'failed', message: outcome.message }
            : { status: 'cancelled', message: 'Payment was cancelled' };
        }
        const order = await paymentApi.verify(callback);
        return { status: 'success', order };
      } finally {
        setProcessing(false);
        setFakeCheckout(null);
      }
    },
    [collectPayment],
  );

  return { pay, processing, fakeCheckout };
}
