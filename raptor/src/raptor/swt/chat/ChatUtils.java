/**
 * New BSD License
 * http://www.opensource.org/licenses/bsd-license.php
 * Copyright 2009-2016 RaptorProject (https://github.com/Raptor-Fics-Interface/Raptor)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the RaptorProject nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package raptor.swt.chat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import raptor.Raptor;
import raptor.action.ActionUtils;
import raptor.action.RaptorAction;
import raptor.action.RaptorAction.RaptorActionContainer;
import raptor.action.SeparatorAction;
import raptor.action.chat.FicsSeekAction;
import raptor.action.chat.PendingOffersAction;
import raptor.action.chat.PrependAction;
import raptor.action.chat.SpeakChannelTellsAction;
import raptor.action.chat.SpeakPersonTellsAction;
import raptor.action.chat.SpeakWhispersAction;
import raptor.action.chat.TellsMissedWhileIWasAwayAction;
import raptor.action.chat.ToggleScrollLock;
import raptor.chat.ChatEvent;
import raptor.chat.ChatLogger.ChatEventParseListener;
import raptor.chat.ChatType;
import raptor.connector.Connector;
import raptor.connector.fics.FicsConnector;
import raptor.international.L10n;
import raptor.service.ActionScriptService;
import raptor.service.ThreadService;
import raptor.service.UserTagService;
import raptor.swt.SWTUtils;
import raptor.swt.chat.controller.BughousePartnerController;
import raptor.swt.chat.controller.ChannelController;
import raptor.swt.chat.controller.GameChatController;
import raptor.swt.chat.controller.PersonController;
import raptor.swt.chat.controller.RegExController;
import raptor.swt.chat.controller.ToolBarItemKey;
import raptor.util.BrowserUtils;
import raptor.util.RaptorLogger;
import raptor.util.RaptorRunnable;

public class ChatUtils {
	public static final String FORWARD_CHAR = " `1234567890-=qwertyuiop[]\\asdfghjkl;'zxcvbnm,./?><MNBVCXZ\":LKJHGFDSA|}{POIUYTREWQ+_)(*&^%$#@!~";
	private static final RaptorLogger LOG = RaptorLogger.getLog(ChatUtils.class);
	public static final String whiteSpaceChars = " \r\n\t";
	protected static L10n local = L10n.getInstance();

	protected static final Pattern urlPattern = Pattern.compile("[\\!\\#\\$\\&\\-\\;\\=\\?\\-\\[\\]\\_a-z\\~]+\\.[^ ]+",
			Pattern.CASE_INSENSITIVE);

	public static boolean isWhiteSpaceChar(char c) {
		return whiteSpaceChars.indexOf(c) != -1;
	}

	public static void addActionsToToolbar(final ChatConsoleController controller, RaptorActionContainer container,
			ToolBar toolbar) {
		RaptorAction[] toolbarActions = ActionScriptService.getInstance().getActions(container);

		for (RaptorAction action : toolbarActions) {
			ToolItem item = createToolItem(action, controller, toolbar);

			if (LOG.isDebugEnabled()) {
				LOG.debug("Added " + action + " to toolbar " + item);
			}
		}
		new ToolItem(toolbar, SWT.SEPARATOR);
	}

	/**
	 * Appends all of the previous chat events to the controller. This method
	 * executes asynchronously.
	 */
	public static void appendPreviousChatsToController(final ChatConsole console) {
		ThreadService.getInstance().run(new Runnable() {
			public void run() {
				console.getController().setSoundDisabled(true);
				console.getController().getConnector().getChatService().getChatLogger()
						.parseFile(new ChatEventParseListener() {

					public boolean onNewEventParsed(final ChatEvent event) {
						console.getDisplay().syncExec(new RaptorRunnable(console.getController().getConnector()) {
							@Override
							public void execute() {

								if (!console.isDisposed()) {
									if (console.getController().isAcceptingChatEvent(event)) {
										console.getController().onChatEvent(event);
									}
								}
							}
						});
						return true;
					}

					public void onParseCompleted() {
						console.getController().setSoundDisabled(false);
					}
				});
			}
		});
	}

	/**
	 * Returns the character at the specified position in the StyledText.
	 */
	public static char charAt(StyledText text, int position) {
		return text.getContent().getTextRange(position, 1).charAt(0);
	}

	/**
	 * Returns null if the current position isn't quoted text, otherwise returns
	 * the text in quotes. Both single and double quotes are supported.
	 */
	public static String getQuotedText(StyledText text, int position) {
		try {
			int quoteStart = -1;
			int quoteEnd = -1;

			int currentPosition = position;
			char currentChar = charAt(text, currentPosition);

			if (currentChar == '\"' || currentChar == '\'') {
				quoteEnd = position;
				currentChar = charAt(text, --currentPosition);
			}

			while (currentChar != '\"' && currentChar != '\'') {
				if (currentChar == '\r' || currentChar == '\n') {
					return null;
				}
				currentChar = charAt(text, --currentPosition);
			}

			quoteStart = currentPosition;

			if (quoteEnd == -1) {
				currentPosition = position + 1;
				currentChar = charAt(text, currentPosition);

				while (currentChar != '\"' && currentChar != '\'') {
					if (currentChar == '\r' || currentChar == '\n') {
						return null;
					}
					currentChar = charAt(text, ++currentPosition);
				}

				quoteEnd = currentPosition;
			}
			return text.getText(quoteStart + 1, quoteEnd - 1);
		} catch (Exception e) {
			return null;
		}
	}

	public static String getUrl(String text) {
		String result = null;
		if (StringUtils.isNotBlank(text)) {

			while (text.length() > 0 && StringUtils.contains("(<#/", text.charAt(0))) {
				text = text.substring(1);
			}

			while (text.length() > 0 && StringUtils.contains(")>;", text.charAt(text.length() - 1))) {
				text = text.substring(0, text.length() - 1);
			}

			if ((text.startsWith("http://") || text.startsWith("https://"))) {
				result = text;

			} else if ((text.endsWith(".com") || text.endsWith(".org") || text.endsWith(".gov") || text.endsWith(".edu")
					|| text.startsWith("www.")) || urlPattern.matcher(text).matches()) {
				result = "http://" + text;
			}
		}
		return result;
	}

	/**
	 * Returns the url at the specified position, null if there is not one. This
	 * method handles ICS wrapping and will remove it and return just the url.
	 */
	public static String getUrl(StyledText text, int position) {
		String candidateWord = getWord(text, position);

		if (candidateWord != null) {
			return getUrl(candidateWord);
		} else {
			return null;
		}
	}

	/**
	 * Returns the word at the specified position, null if there is not one.
	 */
	public static String getWord(StyledText text, int position) {
		try {

			int lineStart;
			int lineEnd;

			int currentPosition = position;
			char currentChar = charAt(text, currentPosition);

			while (currentPosition >= 0 && !isWhiteSpaceChar(currentChar)) {
				currentChar = charAt(text, currentPosition--);
			}

			if (isWhiteSpaceChar(currentChar)) {
				currentPosition++;
			}

			lineStart = currentPosition;

			currentPosition = position;
			currentChar = charAt(text, currentPosition);

			while (currentPosition < text.getCharCount() && !isWhiteSpaceChar(currentChar)) {
				currentChar = charAt(text, currentPosition++);
			}
			if (isWhiteSpaceChar(currentChar)) {
				currentPosition--;
			}

			lineEnd = currentPosition;

			if (lineStart < lineEnd) {
				return trimDateStampFromWord(text.getText(lineStart + 1, lineEnd - 1));
			} else {
				return null;
			}

		} catch (Exception e) {
			LOG.info("Error obtaining word: ", e);
			return null;
		}
	}

	public static void openChannelTab(Connector connector, String channel, boolean isSelected) {
		if (!Raptor.getInstance().getWindow().containsChannelItem(connector, channel)) {
			ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(new ChannelController(connector, channel));
			Raptor.getInstance().getWindow().addRaptorWindowItem(windowItem, false, isSelected);
			ChatUtils.appendPreviousChatsToController(windowItem.getConsole());
		}
	}

	public static void openGameChatTab(Connector connector, String gameId, boolean isSelected) {
		if (!Raptor.getInstance().getWindow().containsGameChatTab(connector, gameId)) {
			ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(new GameChatController(connector, gameId));
			Raptor.getInstance().getWindow().addRaptorWindowItem(windowItem, false, isSelected);
			ChatUtils.appendPreviousChatsToController(windowItem.getConsole());
		}
	}

	public static void openPartnerTab(Connector connector, boolean isSelected) {
		if (!Raptor.getInstance().getWindow().containsPartnerTellItem(connector)) {
			ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(new BughousePartnerController(connector));
			Raptor.getInstance().getWindow().addRaptorWindowItem(windowItem, false, isSelected);
			ChatUtils.appendPreviousChatsToController(windowItem.getConsole());
		}
	}

	public static void openPersonTab(Connector connector, String person, boolean isSelected) {
		if (!Raptor.getInstance().getWindow().containsPersonalTellItem(connector, person)) {
			ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(new PersonController(connector, person));
			Raptor.getInstance().getWindow().addRaptorWindowItem(windowItem, false, isSelected);
			ChatUtils.appendPreviousChatsToController(windowItem.getConsole());
		}
	}

	public static void openRegularExpressionTab(Connector connector, String regularExpression, boolean isSelected) {
		if (!Raptor.getInstance().getWindow().containsPartnerTellItem(connector)) {
			ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(
					new RegExController(connector, regularExpression));
			Raptor.getInstance().getWindow().addRaptorWindowItem(windowItem, false, isSelected);
			ChatUtils.appendPreviousChatsToController(windowItem.getConsole());
		}
	}

	public static String stripDoubleUrls(String word) {
		if (StringUtils.countMatches(word, "http://") == 2 || StringUtils.countMatches(word, "https://") == 2) {
			int httpIndex = word.indexOf("http", 1);
			return word.substring(0, httpIndex).trim();
		} else {
			return word;
		}
	}

	public static String trimDateStampFromWord(String word) {
		if (word.startsWith("[")) {
			int closingBrace = word.indexOf(']');
			if (closingBrace != -1) {
				return word.substring(closingBrace + 1);
			}
		}
		return word;
	}

	private static void addPersonTabsMenu(Menu menu, final Connector connector, final String person) {
		MenuItem addTabsItem = new MenuItem(menu, SWT.CASCADE);
		addTabsItem.setText(local.getString("chatUtils20"));
		Menu addTabs = new Menu(menu);
		addTabsItem.setMenu(addTabs);

		MenuItem item = new MenuItem(addTabs, SWT.PUSH);
		item.setText(local.getString("chatUtils1") + person);
		item.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				if (!Raptor.getInstance().getWindow().containsPersonalTellItem(connector, person)) {
					ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(
							new PersonController(connector, person));
					Raptor.getInstance().getWindow().addRaptorWindowItem(windowItem, false);
					ChatUtils.appendPreviousChatsToController(windowItem.console);
				}
			}
		});

		if (connector instanceof FicsConnector) {
			MenuItem gamebotItem = new MenuItem(addTabs, SWT.PUSH);
			gamebotItem.setText(local.getString("chatUtils2") + person);
			gamebotItem.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event e) {
					SWTUtils.openGamesBotWindowItem((FicsConnector) connector, person);
				}
			});
		}
	}

	private static void addPersonCommandsMenu(Menu menu, final Connector connector, final String person) {
		MenuItem commandsItem = new MenuItem(menu, SWT.CASCADE);
		commandsItem.setText(local.getString("chatUtils21"));
		Menu commands = new Menu(menu);
		commandsItem.setMenu(commands);

		final String[][] connectorPersonQuickItems = connector.getPersonCommandActions(person);
		if (connectorPersonQuickItems != null) {
			for (int i = 0; i < connectorPersonQuickItems.length; i++) {
				if (connectorPersonQuickItems[i][0].equals("separator")) {
					new MenuItem(commands, SWT.SEPARATOR);
				} else {
					MenuItem item = new MenuItem(commands, SWT.PUSH);
					item.setText(connectorPersonQuickItems[i][0]);
					final int index = i;
					item.addListener(SWT.Selection, new Listener() {
						public void handleEvent(Event e) {
							connector.sendMessage(connectorPersonQuickItems[index][1]);
						}
					});
				}
			}
		}
	}

	private static void addPersonMatchCommandsMenu(Menu menu, final Connector connector, final String person) {
		MenuItem commandsItem = new MenuItem(menu, SWT.CASCADE);
		commandsItem.setText(local.getString("chatUtils22"));
		Menu commands = new Menu(menu);
		commandsItem.setMenu(commands);

		// Add quick items for the connector.
		final String[][] connectorPersonQuickItems = connector.getPersonMatchActions(person);
		if (connectorPersonQuickItems != null) {
			for (int i = 0; i < connectorPersonQuickItems.length; i++) {
				if (connectorPersonQuickItems[i][0].equals("separator")) {
					new MenuItem(commands, SWT.SEPARATOR);
				} else {
					MenuItem item = new MenuItem(commands, SWT.PUSH);
					item.setText(connectorPersonQuickItems[i][0]);
					final int index = i;
					item.addListener(SWT.Selection, new Listener() {
						public void handleEvent(Event e) {
							connector.sendMessage(connectorPersonQuickItems[index][1]);
						}
					});
				}
			}
		}
	}

	private static void addPersonListMenu(Menu menu, final Connector connector, final String person) {
		MenuItem listsMenu = new MenuItem(menu, SWT.CASCADE);
		listsMenu.setText(local.getString("chatUtils23"));
		Menu lists = new Menu(menu);
		listsMenu.setMenu(lists);

		// Add quick items for the connector.
		final String[][] connectorPersonQuickItems = connector.getPersonListActions(person);
		if (connectorPersonQuickItems != null) {
			for (int i = 0; i < connectorPersonQuickItems.length; i++) {
				if (connectorPersonQuickItems[i][0].equals("separator")) {
					new MenuItem(lists, SWT.SEPARATOR);
				} else {
					MenuItem item = new MenuItem(lists, SWT.PUSH);
					item.setText(connectorPersonQuickItems[i][0]);
					final int index = i;
					item.addListener(SWT.Selection, new Listener() {
						public void handleEvent(Event e) {
							connector.sendMessage(connectorPersonQuickItems[index][1]);
						}
					});
				}
			}
		}

		new MenuItem(lists, SWT.SEPARATOR);

		if (!connector.isOnExtendedCensor(person)) {

			MenuItem extCensor = new MenuItem(lists, SWT.PUSH);
			extCensor.setText("+extcensor " + person);
			extCensor.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event e) {
					connector.addExtendedCensor(person);
					connector.publishEvent(new ChatEvent(null, ChatType.INTERNAL,
							local.getString("chatUtils6") + person + local.getString("chatUtils7")));
				}
			});
		} else {
			MenuItem extCensor = new MenuItem(lists, SWT.PUSH);
			extCensor.setText("-extcensor " + person);
			extCensor.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event e) {
					boolean result = connector.removeExtendedCensor(person);
					connector
							.publishEvent(
									new ChatEvent(null, ChatType.INTERNAL,
											result ? local.getString("chatUtils8") + person
													+ local.getString("chatUtils7")
													: " Person " + person + local.getString("chatUtils9")));
				}
			});
		}
		new MenuItem(lists, SWT.SEPARATOR);

		String[] tags = UserTagService.getInstance().getTags();
		Arrays.sort(tags);
		if (tags.length > 0) {
			MenuItem addTagsItem = new MenuItem(lists, SWT.CASCADE);
			addTagsItem.setText(local.getString("chatUtils10") + person + "'");
			Menu addTags = new Menu(lists);
			addTagsItem.setMenu(addTags);

			for (final String tag : tags) {
				MenuItem tagMenuItem = new MenuItem(addTags, SWT.PUSH);
				tagMenuItem.setText(tag);
				tagMenuItem.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event e) {
						UserTagService.getInstance().addUser(tag, person);
						connector.publishEvent(new ChatEvent(null, ChatType.INTERNAL,
								local.getString("chatUtils6") + tag + local.getString("chatUtils12") + person));
					}
				});
			}
		}

		tags = UserTagService.getInstance().getTags(person);
		Arrays.sort(tags);
		if (tags.length > 0) {
			MenuItem addTagsItem = new MenuItem(lists, SWT.CASCADE);
			addTagsItem.setText(local.getString("chatUtils13") + person + "'");
			Menu addTags = new Menu(lists);
			addTagsItem.setMenu(addTags);

			for (final String tag : tags) {
				MenuItem tagMenuItem = new MenuItem(addTags, SWT.PUSH);
				tagMenuItem.setText(tag);
				tagMenuItem.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event e) {
						UserTagService.getInstance().clearTag(tag, person);
						connector.publishEvent(new ChatEvent(null, ChatType.INTERNAL,
								local.getString("chatUtils8") + tag + local.getString("chatUtils15") + person));
					}
				});
			}
		}
	}

	public static void addPersonMenuItems(final Menu menu, final Connector connector, final String personsName) {
		if (connector.isLikelyPerson(personsName)) {
			final String person = connector.parsePerson(personsName);

			addPersonTabsMenu(menu, connector, person);
			addPersonCommandsMenu(menu, connector, person);
			addPersonMatchCommandsMenu(menu, connector, person);
			addPersonListMenu(menu, connector, person);

			if (menu.getItemCount() > 0) {
				new MenuItem(menu, SWT.SEPARATOR);
			}

			if (connector instanceof FicsConnector) {
				MenuItem websiteLookupItem = new MenuItem(menu, SWT.CASCADE);
				websiteLookupItem.setText(local.getString("chatUtils16"));
				Menu websiteMenu = new Menu(menu);
				websiteLookupItem.setMenu(websiteMenu);

				MenuItem ficsGamesHistory = new MenuItem(websiteMenu, SWT.PUSH);
				ficsGamesHistory.setText(local.getString("ficsgamesHistory") + person);
				ficsGamesHistory.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event e) {
						BrowserUtils.openUrl("http://www.ficsgames.org/cgi-bin/search.cgi?player=" + person
								+ "&showhistory=showhistory");
					}
				});

				MenuItem ficsGamesStats = new MenuItem(websiteMenu, SWT.PUSH);
				ficsGamesStats.setText(local.getString("ficsgamesStats") + person);
				ficsGamesStats.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event e) {
						BrowserUtils.openUrl("http://www.ficsgames.org/cgi-bin/search.cgi?player=" + person
								+ "&showstats=showstats");
					}
				});
				new MenuItem(websiteMenu, SWT.SEPARATOR);

				MenuItem bugDBStats = new MenuItem(websiteMenu, SWT.PUSH);
				bugDBStats.setText(local.getString("bughouseDBStats") + person);
				bugDBStats.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event e) {
						BrowserUtils.openUrl("http://bughousedb.com/user/" + person + "/");
					}
				});
			}
		}
	}

	public static boolean processHotkeyActions(ChatConsoleController controller, Event event) {
		if (ActionUtils.isValidModifier(event.stateMask)) {
			RaptorAction action = ActionScriptService.getInstance().getAction(event.stateMask, event.keyCode);
			if (action != null) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Executing action from keybinding: " + action.getName());
				}
				action.setChatConsoleControllerSource(controller);
				action.run();
				return true;
			}
		}
		if (ActionUtils.isValidKeyCodeWithoutModifier(event.keyCode)) {
			RaptorAction action = ActionScriptService.getInstance().getAction(event.stateMask, event.keyCode);
			if (action != null) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Executing action from keybinding: " + action.getName());
				}
				action.setChatConsoleControllerSource(controller);
				action.run();
				return true;
			}
		}
		return false;
	}

	protected static ToolItem createToolItem(final RaptorAction action, final ChatConsoleController controller,
			ToolBar toolbar) {
		ToolItem result = null;

		if (action instanceof SeparatorAction) {
			result = new ToolItem(toolbar, SWT.SEPARATOR);
			return result;
		} else if (action instanceof FicsSeekAction && !(controller.getConnector() instanceof FicsConnector)) {
			return null;
		} else if (action instanceof ToggleScrollLock) {
			result = new ToolItem(toolbar, SWT.CHECK);
			result.setSelection(true);
			result.setToolTipText(local.getString("chatUtils18"));
			controller.addToolItem(ToolBarItemKey.AUTO_SCROLL_BUTTON, result);
		} else if (action instanceof PrependAction) {
			result = new ToolItem(toolbar, SWT.CHECK);
			controller.addToolItem(ToolBarItemKey.PREPEND_TEXT_BUTTON, result);
			result.setSelection(true);
		} else if (action instanceof TellsMissedWhileIWasAwayAction) {
			result = new ToolItem(toolbar, SWT.PUSH);
			controller.addToolItem(ToolBarItemKey.AWAY_BUTTON, result);
			result.setEnabled(false);
		} else if (action instanceof SpeakChannelTellsAction) {
			result = new ToolItem(toolbar, SWT.CHECK);
			controller.addToolItem(ToolBarItemKey.SPEAK_TELLS, result);
			result.setSelection(false);
		} else if (action instanceof SpeakWhispersAction) {
			result = new ToolItem(toolbar, SWT.CHECK);
			controller.addToolItem(ToolBarItemKey.SPEAK_TELLS, result);
			result.setSelection(false);
		} else if (action instanceof SpeakPersonTellsAction) {
			result = new ToolItem(toolbar, SWT.CHECK);
			controller.addToolItem(ToolBarItemKey.SPEAK_TELLS, result);
			result.setSelection(false);
		} else if (action instanceof PendingOffersAction) {
			result = new ToolItem(toolbar, SWT.PUSH);
			controller.addToolItem(ToolBarItemKey.PendingChallenges, result);
		} else {
			result = new ToolItem(toolbar, SWT.PUSH);
		}

		if (result.getText() != null && StringUtils.isBlank(action.getIcon())) {
			result.setText(action.getName());
		} else if (StringUtils.isNotBlank(action.getIcon())) {
			result.setImage(Raptor.getInstance().getIcon(action.getIcon()));
		} else {
			Raptor.getInstance().alert(local.getString("chatUtils19") + action.getName());
		}

		if (StringUtils.isNotBlank(action.getDescription())) {
			result.setToolTipText(action.getDescription());
		}

		result.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				RaptorAction loadedAction = ActionScriptService.getInstance().getAction(action.getName());
				loadedAction.setChatConsoleControllerSource(controller);
				loadedAction.run();
			}
		});
		return result;
	}

	private static boolean isInRanges(int location, List<int[]> ranges) {
		boolean result = false;
		for (int[] range : ranges) {
			if (location >= range[0] && location <= range[1]) {
				result = true;
				break;
			}
		}
		return result;
	}

	private static final String[] TOP_LEVEL_DOMAINS = { ".com ", ".org ", ".edu ", ".gov ", ".uk ", ".net ", ".ca ",
			".de ", ".jp ", ".fr ", ".ru ", ".au ", ".us ", ".ch ", ".it ", ".nl ", ".se ", ".no ", ".es ", ".mil ",
			".com\n", ".org\n", ".edu\n", ".gov\n", ".uk\n", ".net\n", ".ca\n", ".de\n", ".jp\n", ".fr\n", ".ru\n",
			".au\n", ".us\n", ".ch\n", ".it\n", ".nl\n", ".se\n", ".no\n", ".es\n", ".mil\n", ".com/", ".org/", ".edu/",
			".gov/", ".uk/", ".net/", ".ca/", ".de/", ".jp/", ".fr/", ".ru/", ".au/", ".us/", ".ch/", ".it/", ".nl/",
			".se/", ".no/", ".es/", ".mil/" };

	public static int getEndIndexOfUrl(int startIndex, List<int[]> linkRanges, String message, StringBuilder dom) {
		int endIndex = message.indexOf(TOP_LEVEL_DOMAINS[0]);
		dom.append(TOP_LEVEL_DOMAINS[0]);
		for (int i = 1; (endIndex == -1 || isInRanges(startIndex, linkRanges)) && i < TOP_LEVEL_DOMAINS.length; i++) {
			endIndex = message.indexOf(TOP_LEVEL_DOMAINS[i]);
			dom.delete(0, dom.length());
			dom.append(TOP_LEVEL_DOMAINS[i]);
		}

		if (endIndex != -1 && isInRanges(startIndex, linkRanges)) {
			endIndex = -1;
		}
		return endIndex;
	}

	public static int getEndIndexOfUrl(int startIndex, List<int[]> linkRanges, String message, int fromIndex,
			StringBuilder dom) {
		int endIndex = message.indexOf(TOP_LEVEL_DOMAINS[0], fromIndex);
		dom.append(TOP_LEVEL_DOMAINS[0]);
		for (int i = 1; (endIndex == -1 || isInRanges(startIndex, linkRanges)) && i < TOP_LEVEL_DOMAINS.length; i++) {
			endIndex = message.indexOf(TOP_LEVEL_DOMAINS[i], fromIndex);
			dom.delete(0, dom.length());
			dom.append(TOP_LEVEL_DOMAINS[i]);
		}

		if (endIndex != -1 && isInRanges(startIndex, linkRanges)) {
			endIndex = -1;
		}
		return endIndex;
	}

}