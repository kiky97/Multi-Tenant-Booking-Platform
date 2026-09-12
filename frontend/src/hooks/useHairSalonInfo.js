import { useEffect, useState } from 'react';
import { publicApi } from '../api/publicApi';
import { reportAppError } from '../utils/appErrorBus';

let cachedInfo = null;
let pendingRequest = null;
let failureReported = false;

export default function useHairSalonInfo() {
    const [info, setInfo] = useState(cachedInfo);

    useEffect(() => {
        let active = true;

        if (cachedInfo) {
            return undefined;
        }

        if (!pendingRequest) {
            pendingRequest = publicApi
                .getHairSalon()
                .then((data) => {
                    cachedInfo = data;
                    return data;
                })
                .catch((error) => {
                    if (!failureReported) {
                        failureReported = true;
                        reportAppError(error, {
                            source: 'hair-salon-info',
                            message: 'Some salon details could not be loaded. Showing fallback contact information for now.'
                        });
                    }

                    return null;
                })
                .finally(() => {
                    pendingRequest = null;
                });
        }

        pendingRequest
            .then((data) => {
                if (active && data) setInfo(data);
            })
            .catch((error) => {
                reportAppError(error, {
                    source: 'hair-salon-info',
                    level: 'warn',
                    message: 'Salon details are temporarily unavailable.',
                    notify: false
                });
            });

        return () => {
            active = false;
        };
    }, []);

    return info;
}
