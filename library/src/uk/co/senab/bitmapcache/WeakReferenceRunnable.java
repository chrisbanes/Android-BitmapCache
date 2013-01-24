package uk.co.senab.bitmapcache;

import java.lang.ref.WeakReference;

abstract class WeakReferenceRunnable<T> implements Runnable {

	private final WeakReference<T> mObjectRef;

	public WeakReferenceRunnable(T object) {
		mObjectRef = new WeakReference<T>(object);
	}

	@Override
	public final void run() {
		T object = mObjectRef.get();

		if (null != object) {
			run(object);
		}
	}

	public abstract void run(T object);

}
