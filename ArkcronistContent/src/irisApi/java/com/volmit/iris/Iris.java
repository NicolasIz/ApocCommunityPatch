package com.volmit.iris;

/** Signature of Iris 3.9.2's main class, as far as ArkcronistContent uses it. Not packaged. */
public class Iris {

    public static <T> T service(Class<T> c) {
        throw new UnsupportedOperationException("compile-time signature only");
    }
}
