package org.ovirt.mobile.movirt.ui.triggers;

import android.app.Activity;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.WindowFeature;
import org.androidannotations.annotations.res.StringRes;

import org.androidannotations.annotations.ItemClick;
import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.model.EntityMapper;
import org.ovirt.mobile.movirt.model.EntityType;
import org.ovirt.mobile.movirt.model.condition.Condition;
import org.ovirt.mobile.movirt.model.trigger.Trigger;
import org.ovirt.mobile.movirt.model.Vm;
import org.ovirt.mobile.movirt.model.condition.CpuThresholdCondition;
import org.ovirt.mobile.movirt.model.condition.MemoryThresholdCondition;
import org.ovirt.mobile.movirt.model.condition.StatusCondition;
import org.ovirt.mobile.movirt.provider.OVirtContract;
import org.ovirt.mobile.movirt.provider.ProviderFacade;
import org.ovirt.mobile.movirt.util.CursorAdapterLoader;

import static org.ovirt.mobile.movirt.provider.OVirtContract.Trigger.*;

@EActivity(R.layout.activity_edit_triggers)
@OptionsMenu(R.menu.triggers)
public class EditTriggersActivity extends Activity implements BaseTriggerDialogFragment.TriggerActivity {
    public static final String EXTRA_TARGET_ENTITY_ID = "target_entity";
    public static final String EXTRA_TARGET_ENTITY_NAME = "target_name";
    public static final String EXTRA_SCOPE = "scope";

    private static final String[] PROJECTION = new String[] {
            OVirtContract.Trigger.CONDITION,
            OVirtContract.Trigger.NOTIFICATION,
    };

    private String targetEntityId;
    private String targetEntityName;

    private Trigger.Scope triggerScope;

    @Bean
    ProviderFacade provider;

    @StringRes(R.string.whole_datacenter)
    String GLOBAL_SCOPE;

    @StringRes(R.string.cluster_scope)
    String CLUSTER_SCOPE;

    @StringRes(R.string.vm_scope)
    String ITEM_SCOPE;

    @StringRes(R.string.trigger_title_format)
    String TITLE_FORMAT;

    @AfterViews
    void init() {
        targetEntityId = getIntent().getStringExtra(EXTRA_TARGET_ENTITY_ID);
        targetEntityName = getIntent().getStringExtra(EXTRA_TARGET_ENTITY_NAME);
        triggerScope = (Trigger.Scope) getIntent().getSerializableExtra(EXTRA_SCOPE);

        setTitle(String.format(TITLE_FORMAT, getScopeText()));

        SimpleCursorAdapter triggerAdapter = new SimpleCursorAdapter(this,
                                                                     R.layout.trigger_item,
                                                                     null,
                                                                     PROJECTION,
                                                                     new int[]{R.id.trigger_condition, R.id.trigger_notification});
        triggerAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                TextView textView = (TextView) view;
                Trigger<Vm> trigger = (Trigger<Vm>) EntityMapper.TRIGGER_MAPPER.fromCursor(cursor);
                if (columnIndex == cursor.getColumnIndex(OVirtContract.Trigger.NOTIFICATION)) {
                    textView.setText(trigger.getNotificationType().getDisplayResourceId());
                } else if (columnIndex == cursor.getColumnIndex(OVirtContract.Trigger.CONDITION)) {
                    textView.setText(getConditionString(trigger.getCondition()));
                }
                return true;
            }
        });

        CursorAdapterLoader cursorAdapterLoader = new CursorAdapterLoader(triggerAdapter) {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                return provider
                        .query(Trigger.class)
                        .where(ENTITY_TYPE, getEntityType().toString())
                        .where(SCOPE, getScope().toString())
                        .where(TARGET_ID, getTargetId())
                        .asLoader();
            }
        };

        triggersListView.setAdapter(triggerAdapter);
        triggersListView.setEmptyView(findViewById(android.R.id.empty));

        getLoaderManager().initLoader(0, null, cursorAdapterLoader);
    }

    private String getScopeText() {
        switch (triggerScope) {
            case GLOBAL:
                return GLOBAL_SCOPE;
            case CLUSTER:
                return String.format(CLUSTER_SCOPE, targetEntityName);
            case ITEM:
                return String.format(ITEM_SCOPE, targetEntityName);
        }
        return "unexpected scope";
    }

    private String getConditionString(Condition<Vm> triggerCondition) {
        StringBuilder builder =  new StringBuilder();
        if (triggerCondition instanceof CpuThresholdCondition) {
            CpuThresholdCondition condition = (CpuThresholdCondition) triggerCondition;
            builder.append("CPU above ").append(condition.percentageLimit).append("%");
        } else if (triggerCondition instanceof MemoryThresholdCondition) {
            MemoryThresholdCondition condition = (MemoryThresholdCondition) triggerCondition;
            builder.append("Memory above ").append(condition.percentageLimit).append("%");
        } else if (triggerCondition instanceof StatusCondition) {
            StatusCondition condition = (StatusCondition) triggerCondition;
            builder.append("Status is ").append(condition.status.toString());
        }
        return builder.toString();
    }

    @ViewById
    ListView triggersListView;

    @OptionsItem(R.id.action_add_trigger)
    void addTrigger() {
        AddTriggerDialogFragment dialog = new AddTriggerDialogFragment_();
        dialog.show(getFragmentManager(), "");
    }

    @ItemClick
    void triggersListViewItemClicked(Cursor cursor) {
        EditTriggerDialogFragment dialog = new EditTriggerDialogFragment_();
        dialog.setTrigger((Trigger<Vm>) EntityMapper.TRIGGER_MAPPER.fromCursor(cursor));
        dialog.show(getFragmentManager(), "");
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.VM;
    }

    @Override
    public Trigger.Scope getScope() {
        return triggerScope;
    }

    @Override
    public String getTargetId() {
        return targetEntityId;
    }
}
