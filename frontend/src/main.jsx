import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { Toaster } from 'react-hot-toast';
import App from './App';
import { AuthProvider } from './context/AuthContext';
import { CartProvider } from './context/CartContext';
import './index.css';

// Provider order matters: CartProvider uses useAuth(), so it sits inside AuthProvider.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <CartProvider>
          <App />
          <Toaster position="top-center" toastOptions={{ duration: 3500, style: { fontSize: '14px' } }} />
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
