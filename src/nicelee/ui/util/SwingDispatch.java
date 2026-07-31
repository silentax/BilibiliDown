package nicelee.ui.util;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

/**
 * Swing 事件分发线程（EDT）调度工具。
 *
 * <p>网络、磁盘和下载工作应在后台线程执行；所有 Swing 组件的创建和修改
 * 都通过此类回到 EDT。</p>
 */
public final class SwingDispatch {

	private SwingDispatch() {
	}

	public static void runLater(Runnable task) {
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	public static void runAndWait(Runnable task) {
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
			return;
		}
		try {
			SwingUtilities.invokeAndWait(task);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("等待 Swing EDT 时被中断", e);
		} catch (InvocationTargetException e) {
			throw propagate(e.getCause());
		}
	}

	public static <T> T callAndWait(final Callable<T> task) {
		if (SwingUtilities.isEventDispatchThread()) {
			return call(task);
		}
		final AtomicReference<T> result = new AtomicReference<T>();
		final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
		runAndWait(new Runnable() {
			@Override
			public void run() {
				try {
					result.set(task.call());
				} catch (Throwable e) {
					error.set(e);
				}
			}
		});
		if (error.get() != null) {
			throw propagate(error.get());
		}
		return result.get();
	}

	private static <T> T call(Callable<T> task) {
		try {
			return task.call();
		} catch (Throwable e) {
			throw propagate(e);
		}
	}

	private static RuntimeException propagate(Throwable error) {
		if (error instanceof RuntimeException) {
			return (RuntimeException) error;
		}
		if (error instanceof Error) {
			throw (Error) error;
		}
		return new IllegalStateException(error);
	}
}
