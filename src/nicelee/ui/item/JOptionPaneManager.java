package nicelee.ui.item;

import java.util.concurrent.ConcurrentLinkedQueue;

import nicelee.ui.Global;
import nicelee.ui.util.SwingDispatch;

public class JOptionPaneManager {

	static JOptionPaneManager instance4CommonMsg = new JOptionPaneManager();
	static JOptionPaneManager instance4ErrMsg = new JOptionPaneManager();

	public static void showMsgWithNewThread(String title, String msg) {
		instance4CommonMsg.showMsgWithNewThread0(title, msg, false);
	}

	public static void alertErrMsgWithNewThread(String title, String msg) {
		instance4ErrMsg.showMsgWithNewThread0(title, msg, true);
	}

	private final ConcurrentLinkedQueue<Object> promptTokens = new ConcurrentLinkedQueue<Object>();

	/**
	 * 保留旧方法名以兼容调用方；实际统一调度到 Swing EDT。
	 */
	private void showMsgWithNewThread0(final String title, final String msg, boolean isErrMsg) {
		if (!Global.isAlertIfDownloded && !isErrMsg) {
			return;
		}
		final Object token = new Object();
		synchronized (promptTokens) {
			if (promptTokens.size() >= Global.maxAlertPrompt) {
				return;
			}
			promptTokens.add(token);
		}

		SwingDispatch.runLater(new Runnable() {
			@Override
			public void run() {
				if (!promptTokens.contains(token)) {
					return;
				}
				Object[] options = { "关闭", "关闭所有" };
				int selected = JOptionPane.showOptionDialog(null, msg, title, JOptionPane.YES_NO_OPTION,
						JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
				synchronized (promptTokens) {
					if (selected == 1) {
						promptTokens.clear();
					} else {
						promptTokens.remove(token);
					}
				}
			}
		});
	}
}
