package nicelee.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.EmptyBorder;

import nicelee.bilibili.annotations.Config;
import nicelee.bilibili.util.ConfigUtil;
import nicelee.ui.item.MJButton;
import nicelee.ui.item.JOptionPane;
import nicelee.ui.util.SwingDispatch;

/** 响应式设置页。配置文件写入在后台执行，Swing 状态只在 EDT 更新。 */
public class TabSettings extends JPanel implements ActionListener {

	private static final long serialVersionUID = 302743425054589939L;
	private static final int GAP = 8;

	private final JPanel settingsContent = new ResponsiveSettingsPanel();
	private final JScrollPane settingsScrollPane = new JScrollPane(settingsContent);
	private final JTextField searchField = new JTextField();
	private final JButton searchButton = new MJButton("筛选");
	private final JButton clearButton = new MJButton("清空");
	private final JButton saveButton = new MJButton("保存");
	private final JButton resetButton = new MJButton("重置");
	private final JButton closeButton = new MJButton("关闭");
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);
	private final List<SettingBinding> bindings = new ArrayList<SettingBinding>();
	private final AtomicBoolean saving = new AtomicBoolean(false);

	public TabSettings() {
		initUI();
	}

	public static void openSettingTab() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingDispatch.runLater(new Runnable() {
				@Override
				public void run() {
					openSettingTab();
				}
			});
			return;
		}
		for (int index = 0; index < Global.tabs.getTabCount(); index++) {
			Component component = Global.tabs.getComponentAt(index);
			if (component instanceof TabSettings) {
				Global.tabs.setSelectedComponent(component);
				return;
			}
		}

		final TabSettings panel = new TabSettings();
		JLabel label = new JLabel("设置页");
		Global.tabs.addTab("设置页", panel);
		Global.tabs.setTabComponentAt(Global.tabs.indexOfComponent(panel), label);
		Global.tabs.setSelectedComponent(panel);
		label.setToolTipText("双击关闭设置页");
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() >= 2) {
					panel.closeIfIdle();
				} else {
					Global.tabs.setSelectedComponent(panel);
				}
			}
		});
	}

	private void initUI() {
		setLayout(new BorderLayout(0, GAP));
		setBorder(new EmptyBorder(12, 12, 12, 12));

		JPanel header = new JPanel(new BorderLayout(0, GAP));
		JLabel tips = new JLabel("设置保存后，部分选项需要重启应用才会生效。");
		tips.setFont(tips.getFont().deriveFont(java.awt.Font.BOLD, 16.0f));
		header.add(tips, BorderLayout.NORTH);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, GAP, 0));
		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				saveSettingsInBackground();
			}
		});
		resetButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				resetEditors();
			}
		});
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				closeIfIdle();
			}
		});
		actions.add(saveButton);
		actions.add(resetButton);
		actions.add(closeButton);
		header.add(actions, BorderLayout.CENTER);
		header.add(createSearchPanel(), BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		settingsContent.setBorder(new EmptyBorder(4, 4, 4, 4));
		settingsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		settingsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		settingsScrollPane.getVerticalScrollBar().setUnitIncrement(20);
		add(settingsScrollPane, BorderLayout.CENTER);

		statusLabel.setBorder(new EmptyBorder(2, 4, 0, 4));
		add(statusLabel, BorderLayout.SOUTH);
		buildSettingRows();
	}

	private JPanel createSearchPanel() {
		JPanel searchPanel = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 0, GAP);
		constraints.fill = GridBagConstraints.HORIZONTAL;

		constraints.gridx = 0;
		constraints.weightx = 1.0;
		searchField.setToolTipText("可按设置说明、配置 key 或当前值筛选");
		searchField.addActionListener(this);
		searchPanel.add(searchField, constraints);

		constraints.gridx = 1;
		constraints.weightx = 0.0;
		searchButton.addActionListener(this);
		searchPanel.add(searchButton, constraints);

		constraints.gridx = 2;
		constraints.insets = new Insets(0, 0, 0, 0);
		clearButton.addActionListener(this);
		searchPanel.add(clearButton, constraints);
		return searchPanel;
	}

	private void buildSettingRows() {
		settingsContent.removeAll();
		bindings.clear();
		int rowIndex = 0;
		for (Field field : Global.class.getDeclaredFields()) {
			Config config = field.getAnnotation(Config.class);
			if (config == null || config.note().isEmpty()) {
				continue;
			}
			String key = config.key();
			String configuredValue = Global.settings.get(key);
			SettingBinding binding = createBinding(key, config.note(), configuredValue, config.valids(),
					config.pathType());
			bindings.add(binding);

			GridBagConstraints rowConstraints = new GridBagConstraints();
			rowConstraints.gridx = 0;
			rowConstraints.gridy = rowIndex++;
			rowConstraints.weightx = 1.0;
			rowConstraints.fill = GridBagConstraints.HORIZONTAL;
			rowConstraints.anchor = GridBagConstraints.NORTH;
			rowConstraints.insets = new Insets(0, 0, GAP, 0);
			settingsContent.add(binding.row, rowConstraints);
		}

		GridBagConstraints filler = new GridBagConstraints();
		filler.gridx = 0;
		filler.gridy = rowIndex;
		filler.weightx = 1.0;
		filler.weighty = 1.0;
		filler.fill = GridBagConstraints.BOTH;
		settingsContent.add(new JPanel(), filler);
		settingsContent.revalidate();
		settingsContent.repaint();
	}

	private SettingBinding createBinding(String key, String note, String configuredValue, String[] validValues,
			String pathType) {
		JLabel nameLabel = new JLabel(note);
		nameLabel.setToolTipText("<html>" + note + "<br>" + key + "</html>");
		nameLabel.setOpaque(true);
		nameLabel.setBorder(new EmptyBorder(4, 6, 4, 8));

		JComponent editor;
		if (validValues.length > 0) {
			JComboBox<String> comboBox = new JComboBox<String>(validValues);
			if (configuredValue != null) {
				comboBox.setSelectedItem(configuredValue);
			}
			editor = comboBox;
		} else if (isSensitiveKey(key)) {
			editor = new JPasswordField(configuredValue == null ? "" : configuredValue);
		} else {
			editor = new JTextField(configuredValue == null ? "" : configuredValue);
		}

		JButton chooserButton = null;
		if (!pathType.isEmpty() && editor instanceof JTextField) {
			chooserButton = new JButton("选择…");
		}
		final SettingBinding binding = new SettingBinding(key, note, nameLabel, editor, chooserButton);
		binding.originalValue = binding.getValue();

		if (editor instanceof JComboBox) {
			@SuppressWarnings("unchecked")
			JComboBox<String> comboBox = (JComboBox<String>) editor;
			comboBox.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent event) {
					if (event.getStateChange() == ItemEvent.SELECTED) {
						binding.updateChangedState();
					}
				}
			});
		} else if (editor instanceof JPasswordField) {
			editor.addFocusListener(new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent event) {
					binding.updateChangedState();
				}
			});
		} else {
			((JTextField) editor).getDocument().addDocumentListener(new DocumentListener() {
				@Override
				public void insertUpdate(DocumentEvent event) {
					binding.updateChangedState();
				}

				@Override
				public void removeUpdate(DocumentEvent event) {
					binding.updateChangedState();
				}

				@Override
				public void changedUpdate(DocumentEvent event) {
					binding.updateChangedState();
				}
			});
		}

		if (chooserButton != null) {
			final boolean selectFile = !pathType.startsWith("dir");
			chooserButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					openPathChooser(binding, selectFile);
				}
			});
		}
		return binding;
	}

	private boolean isSensitiveKey(String key) {
		return "bilibili.download.push.token".equals(key);
	}

	private void openPathChooser(SettingBinding binding, boolean selectFile) {
		JFileChooser chooser = binding.getValue().isEmpty() ? new JFileChooser() : new JFileChooser(binding.getValue());
		chooser.setFileSelectionMode(selectFile ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle(selectFile ? "请选择文件" : "请选择文件夹");
		chooser.setApproveButtonText("确定");
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			binding.setValue(chooser.getSelectedFile().getAbsolutePath());
		}
	}

	private void saveSettingsInBackground() {
		if (!saving.compareAndSet(false, true)) {
			return;
		}
		Map<String, String> editableSnapshot;
		synchronized (Global.settings) {
			editableSnapshot = new LinkedHashMap<String, String>(Global.settings);
		}
		for (SettingBinding binding : bindings) {
			editableSnapshot.put(binding.key, binding.getValue());
		}
		final Map<String, String> settingsSnapshot = Collections
				.unmodifiableMap(new LinkedHashMap<String, String>(editableSnapshot));
		setSavingState(true);
		showStatus("正在保存配置...", false);

		Thread saveThread = new Thread(new Runnable() {
			@Override
			public void run() {
				boolean saved;
				try {
					saved = ConfigUtil.saveConfig(settingsSnapshot);
				} catch (RuntimeException error) {
					saved = false;
				}
				final boolean saveSucceeded = saved;
				SwingDispatch.runLater(new Runnable() {
					@Override
					public void run() {
						if (saveSucceeded) {
							synchronized (Global.settings) {
								Global.settings.putAll(settingsSnapshot);
							}
							for (SettingBinding binding : bindings) {
								binding.markSaved(settingsSnapshot.get(binding.key));
							}
							showStatus("保存成功，部分设置将在重启后生效", false);
						} else {
							showStatus("保存失败，请检查配置目录权限后重试", true);
						}
						saving.set(false);
						setSavingState(false);
					}
				});
			}
		}, "Thread-SettingsSave");
		saveThread.setDaemon(true);
		saveThread.start();
	}

	private void setSavingState(boolean busy) {
		saveButton.setEnabled(!busy);
		resetButton.setEnabled(!busy);
		searchField.setEnabled(!busy);
		searchButton.setEnabled(!busy);
		clearButton.setEnabled(!busy);
		closeButton.setEnabled(!busy);
		for (SettingBinding binding : bindings) {
			binding.setEnabled(!busy);
		}
	}

	private void closeIfIdle() {
		if (saving.get()) {
			showStatus("配置正在保存，请稍候再关闭", false);
			return;
		}
		if (hasUnsavedChanges()) {
			Object[] options = { "放弃修改", "继续编辑" };
			int selected = JOptionPane.showOptionDialog(this, "当前设置尚未保存，确定要关闭吗？", "未保存的设置",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			if (selected != 0) {
				return;
			}
		}
		Global.tabs.remove(this);
	}

	private boolean hasUnsavedChanges() {
		for (SettingBinding binding : bindings) {
			if (binding.isChanged()) {
				return true;
			}
		}
		return false;
	}

	private void resetEditors() {
		if (saving.get()) {
			return;
		}
		for (SettingBinding binding : bindings) {
			binding.markSaved(Global.settings.get(binding.key));
		}
		applyFilter();
		showStatus("已恢复到上次保存的配置值", false);
	}

	private void applyFilter() {
		String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
		int visibleCount = 0;
		for (SettingBinding binding : bindings) {
			boolean visible = binding.matches(query);
			binding.row.setVisible(visible);
			if (visible) {
				visibleCount++;
			}
		}
		settingsContent.revalidate();
		settingsContent.repaint();
		if (!query.isEmpty()) {
			showStatus("找到 " + visibleCount + " 项设置；筛选不会丢失未保存的修改", false);
		}
	}

	private void showStatus(String message, boolean error) {
		statusLabel.setForeground(error ? new Color(170, 45, 45) : new Color(45, 105, 65));
		statusLabel.setText(message);
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		if (event.getSource() == clearButton) {
			searchField.setText("");
		}
		applyFilter();
	}

	public JPanel getSettingsContentPanel() {
		return settingsContent;
	}

	public JScrollPane getSettingsScrollPane() {
		return settingsScrollPane;
	}

	public int getEditorCount() {
		return bindings.size();
	}

	public boolean isSensitiveEditorMasked(String key) {
		for (SettingBinding binding : bindings) {
			if (binding.key.equals(key)) {
				return binding.editor instanceof JPasswordField;
			}
		}
		return false;
	}

	private static final class ResponsiveSettingsPanel extends JPanel implements Scrollable {
		private static final long serialVersionUID = 1L;

		private ResponsiveSettingsPanel() {
			super(new GridBagLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return 24;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return Math.max(24, visibleRect.height - 24);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}

	private static final class SettingBinding {
		private final String key;
		private final String note;
		private final JLabel nameLabel;
		private final JComponent editor;
		private final JButton chooserButton;
		private final JPanel row = new JPanel(new GridBagLayout());
		private final Color normalLabelBackground;
		private String originalValue;

		private SettingBinding(String key, String note, JLabel nameLabel, JComponent editor,
				JButton chooserButton) {
			this.key = key;
			this.note = note;
			this.nameLabel = nameLabel;
			this.editor = editor;
			this.chooserButton = chooserButton;
			this.normalLabelBackground = nameLabel.getBackground();
			buildRow();
		}

		private void buildRow() {
			row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
			GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridy = 0;
			constraints.insets = new Insets(3, 3, 3, 3);
			constraints.fill = GridBagConstraints.HORIZONTAL;

			constraints.gridx = 0;
			constraints.weightx = 0.42;
			row.add(nameLabel, constraints);

			constraints.gridx = 1;
			constraints.weightx = 0.58;
			row.add(editor, constraints);

			if (chooserButton != null) {
				constraints.gridx = 2;
				constraints.weightx = 0.0;
				row.add(chooserButton, constraints);
			}
		}

		private String getValue() {
			if (editor instanceof JComboBox) {
				Object selected = ((JComboBox<?>) editor).getSelectedItem();
				return selected == null ? "" : selected.toString();
			}
			if (editor instanceof JPasswordField) {
				char[] password = ((JPasswordField) editor).getPassword();
				try {
					return new String(password).trim();
				} finally {
					Arrays.fill(password, '\0');
				}
			}
			return ((JTextField) editor).getText().trim();
		}

		private void setValue(String value) {
			String normalized = value == null ? "" : value;
			if (editor instanceof JComboBox) {
				((JComboBox<?>) editor).setSelectedItem(normalized.isEmpty() ? null : normalized);
			} else {
				((JTextField) editor).setText(normalized);
			}
			updateChangedState();
		}

		private void markSaved(String value) {
			setValue(value);
			originalValue = getValue();
			updateChangedState();
		}

		private void updateChangedState() {
			nameLabel.setBackground(isChanged() ? new Color(255, 220, 230) : normalLabelBackground);
		}

		private boolean isChanged() {
			return originalValue != null && !originalValue.equals(getValue());
		}

		private boolean matches(String query) {
			return query.isEmpty() || note.toLowerCase(Locale.ROOT).contains(query)
					|| key.toLowerCase(Locale.ROOT).contains(query)
					|| (!(editor instanceof JPasswordField) && getValue().toLowerCase(Locale.ROOT).contains(query));
		}

		private void setEnabled(boolean enabled) {
			editor.setEnabled(enabled);
			if (chooserButton != null) {
				chooserButton.setEnabled(enabled);
			}
		}
	}
}
