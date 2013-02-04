package piuk;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import com.google.bitcoin.core.Transaction;

public class EventListeners {
	public static class EventListener {
		public void onWalletDidChange() {
		};

		public void onCoinsSent(final MyTransaction tx, final long result) {
		};

		public void onCoinsReceived(final MyTransaction tx, final long result) {
		};
	}

	private static final Set<WeakReference<EventListener>> listeners = new HashSet<WeakReference<EventListener>>();

	public static boolean addEventListener(EventListener listener) {
		synchronized (listeners) {
			return listeners.add(new WeakReference<EventListener>(listener));
		}
	} 

	public static boolean removeEventListener(EventListener listener) {
		synchronized (listeners) {
			return listeners.remove(listener);
		}
	}

	public static void invokeOnCoinsReceived(final MyTransaction tx, final long result) {

		synchronized (listeners) {
			for (final WeakReference<EventListener> listener : listeners) {
				if (listener.get() == null)
					return;

				new Thread(new Runnable() {
					@Override
					public void run() {
						EventListener _listener = listener.get();
						if (_listener != null) {
							_listener.onCoinsReceived(tx, result);
						}
					}
				}).start();
			}
		}
	}

	public static void invokeOnCoinsSent(final MyTransaction tx, final long result) {

		synchronized (listeners) {
			for (final WeakReference<EventListener> listener : listeners) {
				if (listener.get() == null)
					return;

				new Thread(new Runnable() {
					@Override
					public void run() {
						EventListener _listener = listener.get();
						if (_listener != null) {
							_listener.onCoinsSent(tx, result);
						}
					}
				}).start();
			}
		}
	}

	public static void invokeWalletDidChange() {

		synchronized (listeners) {
			for (final WeakReference<EventListener> listener : listeners) {

				if (listener.get() == null)
					return;

				new Thread(new Runnable() {
					@Override
					public void run() {
						EventListener _listener = listener.get();
						if (_listener != null) {
							_listener.onWalletDidChange();
						}
					}
				}).start();
			}
		}
	}
}
