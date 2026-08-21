import React, { StrictMode, Component, ErrorInfo, ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.tsx';
import './index.css';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Uncaught React Error:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ minHeight: "100vh", backgroundColor: "#050505", color: "#e2e8f0", padding: "40px 20px", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", fontFamily: "sans-serif" }}>
          <div style={{ maxWidth: "500px", background: "#111", border: "1px solid #333", padding: "30px", borderRadius: "12px", textAlign: "center" }}>
            <h2 style={{ color: "#10b981", fontSize: "18px", textTransform: "uppercase", letterSpacing: "1px", marginBottom: "12px" }}>Workstation Deal Hunter</h2>
            <p style={{ color: "#aaa", fontSize: "13px", lineHeight: "1.6", marginBottom: "20px" }}>A rendering issue occurred. Click below to reload the dashboard.</p>
            <p style={{ color: "#ef4444", fontSize: "11px", fontFamily: "monospace", background: "#1e1111", padding: "10px", borderRadius: "6px", marginBottom: "20px", wordBreak: "break-word" }}>{this.state.error?.message || "Unknown error"}</p>
            <button
              onClick={() => {
                localStorage.clear();
                sessionStorage.clear();
                window.location.reload();
              }}
              style={{ backgroundColor: "#10b981", color: "#000", border: "none", padding: "10px 20px", borderRadius: "6px", fontWeight: "bold", fontSize: "12px", cursor: "pointer", textTransform: "uppercase" }}
            >
              Reset &amp; Reload
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

const rootEl = document.getElementById('root');
if (rootEl) {
  createRoot(rootEl).render(
    <StrictMode>
      <ErrorBoundary>
        <App />
      </ErrorBoundary>
    </StrictMode>,
  );
}
