import { Component } from 'react';
import { reportAppError } from '../utils/appErrorBus';
import { logAppIssue } from '../utils/logger';

class GlobalErrorBoundary extends Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false };
    }

    static getDerivedStateFromError() {
        return { hasError: true };
    }

    componentDidCatch(error, errorInfo) {
        reportAppError(error, {
            source: 'react-error-boundary',
            notify: false
        });

        logAppIssue('error', 'React component stack', {
            name: 'ReactComponentStack',
            message: errorInfo?.componentStack || ''
        });
    }

    render() {
        if (!this.state.hasError) {
            return this.props.children;
        }

        return (
            <div className="min-h-screen bg-[#050505] px-6 py-16 text-ivory">
                <div className="mx-auto max-w-[720px] rounded-[28px] border border-[#c6934b55] bg-[linear-gradient(180deg,rgba(12,12,12,0.97),rgba(6,6,6,0.95))] p-8 text-center shadow-[0_24px_80px_rgba(0,0,0,0.45)]">
                    <p className="text-[11px] uppercase tracking-[0.28em] text-goldBright">
                        Unexpected error
                    </p>
                    <h1 className="mt-4 font-heading text-3xl uppercase tracking-[0.1em] text-ivory">
                        Please refresh and try again
                    </h1>
                    <p className="mt-4 text-sm leading-7 text-smoke">
                        We hit an unexpected problem and stopped this screen to avoid showing broken or incomplete data.
                    </p>
                    <div className="mt-8">
                        <button
                            type="button"
                            className="rounded-full border border-[#d6a65b] px-5 py-3 font-heading text-sm uppercase tracking-[0.16em] text-goldBright transition hover:bg-[#d6a65b] hover:text-black"
                            onClick={() => window.location.reload()}
                        >
                            Reload page
                        </button>
                    </div>
                </div>
            </div>
        );
    }
}

export default GlobalErrorBoundary;
