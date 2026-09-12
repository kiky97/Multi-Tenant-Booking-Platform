import GoldButton from '../ui/GoldButton';

export default function StepNavigation({
    step,
    maxStep = 4,
    onBack,
    onNext,
    nextDisabled = false
}) {
    const showBack = step > 1;
    const showNext = step < maxStep;
    const navigationClassName = `booking-step-navigation ${
        showBack && showNext ? 'booking-step-navigation-dual' : 'booking-step-navigation-single'
    }`;

    return (
        <div className={navigationClassName}>
            {showBack ? (
                <GoldButton type="button" onClick={onBack} className="booking-step-navigation-button">
                    Back
                </GoldButton>
            ) : null}

            {showNext ? (
                <GoldButton
                    type="button"
                    onClick={onNext}
                    disabled={nextDisabled}
                    className="booking-step-navigation-button"
                >
                    Next
                </GoldButton>
            ) : null}
        </div>
    );
}
