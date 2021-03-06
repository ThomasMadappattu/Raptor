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
package raptor.swt;

import org.apache.commons.lang.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import raptor.Quadrant;
import raptor.Raptor;
import raptor.chat.BugGame;
import raptor.chat.Bugger;
import raptor.chat.Partnership;
import raptor.connector.Connector;
import raptor.international.L10n;
import raptor.pref.PreferenceKeys;
import raptor.service.BughouseService;
import raptor.service.BughouseService.BughouseServiceListener;
import raptor.service.ThreadService;
import raptor.swt.RaptorTable.RaptorTableAdapter;
import raptor.util.RaptorRunnable;

public class BugGames extends Composite {

	public static final Quadrant[] MOVE_TO_QUADRANTS = { Quadrant.I,
			Quadrant.II, Quadrant.III, Quadrant.IV, Quadrant.V, Quadrant.VI,
			Quadrant.VII, Quadrant.VIII, Quadrant.IX };

	protected BughouseService service;

	protected static L10n local = L10n.getInstance();

	protected RaptorTable bugGamesTable;
	protected boolean isActive = false;
	protected Runnable timer = new Runnable() {
		public void run() {
			if (isActive && !isDisposed()) {
				service.refreshGamesInProgress();
				ThreadService
						.getInstance()
						.scheduleOneShot(
								Raptor.getInstance()
										.getPreferences()
										.getInt(PreferenceKeys.APP_WINDOW_ITEM_POLL_INTERVAL) * 1000,
								this);
			}
		}
	};

	protected BughouseServiceListener listener = new BughouseServiceListener() {
		public void availablePartnershipsChanged(Partnership[] newPartnerships) {
		}

		public void gamesInProgressChanged(BugGame[] newGamesInProgress) {
			refreshTable();
		}

		public void unpartneredBuggersChanged(Bugger[] newUnpartneredBuggers) {

		}
	};

	public BugGames(Composite parent, BughouseService service) {
		super(parent, SWT.NONE);
		this.service = service;
		init();
		service.addBughouseServiceListener(listener);
	}

	public Connector getConnector() {
		return service.getConnector();
	}

	public void init() {
		setLayout(new GridLayout(1, false));

		bugGamesTable = new RaptorTable(this, SWT.BORDER | SWT.H_SCROLL
				| SWT.V_SCROLL | SWT.SINGLE | SWT.FULL_SELECTION);
		bugGamesTable
				.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		bugGamesTable.addColumn(local.getString("bugGames1"), SWT.LEFT, 10,
				true, null);
		bugGamesTable.addColumn(local.getString("bugGames2"), SWT.LEFT, 14,
				true, null);
		bugGamesTable.addColumn(local.getString("bugGames3"), SWT.LEFT, 30,
				true, null);
		bugGamesTable.addColumn(local.getString("bugGames4"), SWT.LEFT, 30,
				true, null);

		bugGamesTable.addRaptorTableListener(new RaptorTableAdapter() {
			@Override
			public void rowDoubleClicked(MouseEvent event, String[] rowData) {
				if (StringUtils.isNotBlank(rowData[0])) {
					service.getConnector().onObserveGame(rowData[0]);
				}
			}
		});

		bugGamesTable.setSortable(false);

		Composite buttonsComposite = new Composite(this, SWT.NONE);
		buttonsComposite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER,
				true, false));
		buttonsComposite.setLayout(new RowLayout());

		Button obsButton = new Button(buttonsComposite, SWT.PUSH);
		obsButton.setText(local.getString("bugGames8"));
		obsButton.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				synchronized (bugGamesTable) {
					int selectedIndex = bugGamesTable.getTable()
							.getSelectionIndex();
					if (selectedIndex != -1) {
						String gameNumber = bugGamesTable.getTable()
								.getItem(selectedIndex).getText(0);
						if (StringUtils.isNotBlank(gameNumber)) {
							service.getConnector().onObserveGame(gameNumber);
						}
					} else {
						Raptor.getInstance()
								.alert(local.getString("bugGames9"));
					}
				}

			}
		});
		service.refreshGamesInProgress();
		refreshTable();
	}

	public void onActivate() {
		if (!isActive) {
			isActive = true;
			service.refreshGamesInProgress();
			ThreadService
					.getInstance()
					.scheduleOneShot(
							Raptor.getInstance()
									.getPreferences()
									.getInt(PreferenceKeys.APP_WINDOW_ITEM_POLL_INTERVAL) * 1000,
							timer);
		}

	}

	public void onPassivate() {
		if (isActive) {
			isActive = false;
		}
	}

	protected void refreshTable() {
		Raptor.getInstance().getDisplay().asyncExec(new RaptorRunnable() {
			@Override
			public void execute() {
				if (!bugGamesTable.isDisposed()) {
					synchronized (bugGamesTable.getTable()) {

						BugGame[] bugGames = service.getGamesInProgress();
						if (bugGames == null) {
							bugGames = new BugGame[0];
						}

						int rowDataRows = bugGames.length == 0 ? 0
								: bugGames.length * 3 - 1;

						String[][] rowData = new String[rowDataRows][4];
						for (int i = 0; i < bugGames.length; i++) {
							int index = i * 3;
							rowData[index][0] = bugGames[i].getGame1Id();
							rowData[index][1] = bugGames[i].getTimeControl()
									+ " " + (bugGames[i].isRated() ? "r" : "u");
							rowData[index][2] = bugGames[i].getGame1White().getRating() + " " + bugGames[i].getGame1White().getName();
							rowData[index][3] = bugGames[i].getGame1Black().getRating() + " " + bugGames[i].getGame1Black()
									.getName();

							rowData[index + 1][0] = bugGames[i].getGame2Id();
							rowData[index + 1][1] = "";
							rowData[index + 1][2] = bugGames[i].getGame2White().getRating() + " " + bugGames[i].getGame2White().getName();
							rowData[index + 1][3] = bugGames[i].getGame2Black().getRating() + " " + bugGames[i].getGame2Black().getName();

							if (i < (bugGames.length - 1)) {
								rowData[index + 2][0] = "";
								rowData[index + 2][1] = "";
								rowData[index + 2][2] = "";
								rowData[index + 2][3] = "";
							}
						}
						bugGamesTable.refreshTable(rowData);
					}
				}
			}
		});
	}
}