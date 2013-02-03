package piuk;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import com.google.bitcoin.core.Transaction;

import android.os.Handler;

public class EventListeners {
	public static class EventListener {
		public void onWalletDidChange() {
		};

		public void onCoinsSent(final Transaction tx,
				final BigInteger prevBalance, final BigInteger newBalance) {
		};

		public void onCoinsReceived(final Transaction tx,
				final BigInteger prevBalance, final BigInteger newBalance) {
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

	public static void invokeOnCoinsReceived(final Transaction tx,
			final BigInteger prevBalance, final BigInteger newBalance) {

		synchronized (listeners) {
			for (final WeakReference<EventListener> listener : listeners) {
				if (listener.get() == null)
					return;
				
				new Thread(new Runnable() {
					@Override
					public void run() {
						listener.get().onCoinsReceived(tx, prevBalance, newBalance);
					}
				}).start();
			}
		}
	}

	public static void invokeOnCoinsSent(final Transaction tx,
			final BigInteger prevBalance, final BigInteger newBalance) {

		synchronized (listeners) {
			for (final WeakReference<EventListener> listener : listeners) {
				if (listener.get() == null)
					return;
				
				new Thread(new Runnable() {
					@Override
					public void run() {
						listener.get().onCoinsSent(tx, prevBalance, newBalance);
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
						listener.get().onWalletDidChange();
					}
				}).start();
			}
		}
	}
}
