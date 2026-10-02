import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'

interface Props {
  children: ReactNode
  fallbackTitle?: string
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  public override state: State = {
    hasError: false,
    error: null,
  }

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  public override componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('TRC Photo Booth Uncaught Error:', error, errorInfo)
  }

  public override render() {
    if (this.state.hasError) {
      return (
        <div className="error-boundary-card">
          <div className="error-icon-box">
            <AlertTriangle size={32} className="error-triangle-icon" />
          </div>
          <h3 className="error-title">
            {this.props.fallbackTitle || 'Something went wrong'}
          </h3>
          <p className="error-message">
            {this.state.error?.message || 'An unexpected error occurred in this view.'}
          </p>
          <button
            type="button"
            className="error-reset-btn"
            onClick={() => this.setState({ hasError: false, error: null })}
          >
            <RefreshCw size={14} />
            <span>Try Again</span>
          </button>
        </div>
      )
    }

    return this.props.children
  }
}
