package com.volmit.iris.core.service;

import com.volmit.iris.core.link.ExternalDataProvider;

/** Signature of Iris 3.9.2's ExternalDataSVC, as far as ArkcronistContent uses it. Not packaged. */
public class ExternalDataSVC {

    public void registerProvider(ExternalDataProvider provider) {
        throw new UnsupportedOperationException("compile-time signature only");
    }
}
