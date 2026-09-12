export default function BookingStepper({
    currentStep,
    steps,
    onStepChange,
    maxReachedStep = currentStep
}) {
    const maxClickableStep = Math.min(currentStep, maxReachedStep);

    return (
        <div className="w-full max-w-[420px] mx-auto mb-6 px-2">
            <div className="flex items-center w-full">
                {steps.map((step, idx) => {
                    const isActive = currentStep === step.id;
                    const isClickable = step.id <= maxClickableStep;

                    return (
                        <div key={step.id} className="flex items-center flex-1 min-w-0">
                            <div className="flex flex-col items-center w-full min-w-0">
                                <button
                                    type="button"
                                    onClick={() => isClickable && onStepChange(step.id)}
                                    className={`step-circle shrink-0 ${isActive ? 'active' : ''}`}
                                    disabled={!isClickable}
                                >
                                    {step.id}
                                </button>

                                <span
                                    className={`mt-2 text-[9px] sm:text-[11px] tracking-[0.12em] uppercase leading-tight text-center break-words ${isActive ? 'text-goldBright' : 'text-smoke'
                                        }`}
                                >
                                    {step.label}
                                </span>
                            </div>

                            {idx < steps.length - 1 && (
                                <div className="h-px flex-1 mx-2 bg-[#c6934b40] self-start mt-4" />
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
