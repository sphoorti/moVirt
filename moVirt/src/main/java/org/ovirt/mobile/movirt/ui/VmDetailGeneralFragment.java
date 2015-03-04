package org.ovirt.mobile.movirt.ui;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.Receiver;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.res.StringRes;
import org.ovirt.mobile.movirt.Broadcasts;
import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.model.EntityMapper;
import org.ovirt.mobile.movirt.model.Vm;
import org.ovirt.mobile.movirt.model.VmStatistics;
import org.ovirt.mobile.movirt.model.trigger.Trigger;
import org.ovirt.mobile.movirt.provider.ProviderFacade;
import org.ovirt.mobile.movirt.rest.ActionTicket;
import org.ovirt.mobile.movirt.rest.ExtendedVm;
import org.ovirt.mobile.movirt.rest.OVirtClient;
import org.ovirt.mobile.movirt.ui.triggers.EditTriggersActivity;
import org.ovirt.mobile.movirt.ui.triggers.EditTriggersActivity_;

@EFragment(R.layout.fragment_vm_detail_general)
public class VmDetailGeneralFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = VmDetailGeneralFragment.class.getSimpleName();

    private static final String VM_URI = "vm_uri";

    private String vmId = null;

    @ViewById
    TextView statusView;

    @ViewById
    TextView cpuView;

    @ViewById
    TextView memView;

    @ViewById
    TextView memoryView;

    @ViewById
    TextView socketView;

    @ViewById
    TextView coreView;

    @ViewById
    TextView osView;

    @ViewById
    TextView displayView;

    @ViewById
    ProgressBar vncProgress;

    Bundle args;

    @Bean
    OVirtClient client;

    @Bean
    ProviderFacade provider;

    @StringRes(R.string.details_for_vm)
    String VM_DETAILS;

    Vm vm;

    @AfterViews
    void initLoader() {

        hideProgressBar();
        Uri vmUri = getActivity().getIntent().getData();


        args = new Bundle();
        args.putParcelable(VM_URI, vmUri);
        getLoaderManager().initLoader(0, args, this);
        vmId = vmUri.getLastPathSegment();
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().restartLoader(0, args, this);
    }

    @UiThread
    void showProgressBar() {
        vncProgress.setVisibility(View.VISIBLE);
    }

    @UiThread
    void hideProgressBar() {
        vncProgress.setVisibility(View.GONE);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String vmId = args.<Uri>getParcelable(VM_URI).getLastPathSegment();
        return provider.query(Vm.class).id(vmId).asLoader();
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (!data.moveToNext()) {
            Log.e(TAG, "Error loading Vm");
            return;
        }
        vm = EntityMapper.VM_MAPPER.fromCursor(data);
        getActivity().setTitle(String.format(VM_DETAILS, vm.getName()));
        statusView.setText(vm.getStatus().toString());
        cpuView.setText(String.format("%.2f%%", vm.getCpuUsage()));
        memView.setText(String.format("%.2f%%", vm.getMemoryUsage()));

        loadAdditionalVmData(vm);
    }

    // todo move to activity somehow
//    private void updateCommandButtons(Vm vm) {
//        runButton.setClickable(Vm.Command.RUN.canExecute(vm.getStatus()));
//        stopButton.setClickable(Vm.Command.POWEROFF.canExecute(vm.getStatus()));
//        rebootButton.setClickable(Vm.Command.REBOOT.canExecute(vm.getStatus()));
//    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // do nothing
    }

    @UiThread
    public void renderVm(ExtendedVm vm, VmStatistics statistics) {
        Long memoryMB = 0L;
        boolean memoryExceptionFlag = false;
        getActivity().setTitle(String.format(VM_DETAILS, vm.name));
        statusView.setText(vm.status.state);
        cpuView.setText(String.format("%.2f%%", statistics.getCpuUsage()));
        memView.setText(String.format("%.2f%%", statistics.getMemoryUsage()));
        try {
            memoryMB = Long.parseLong(vm.memory);
        }
        catch (Exception e) {
            memoryExceptionFlag = true;
        }
        if(!memoryExceptionFlag) {
            memoryMB = memoryMB / (1024 * 1024);
            memoryView.setText(memoryMB + " MB");
        }
        else {
            memoryView.setText("N/A");
        }
        socketView.setText(vm.cpu.topology.sockets);
        coreView.setText(vm.cpu.topology.cores);
        osView.setText(vm.os.type);
        if (vm.display != null && vm.display.type != null) {
            displayView.setText(vm.display.type);
        }
        else {
            displayView.setText("N/A");
        }

    }


    // todo move to activity somehow
//    private void updateCommandButtons(org.ovirt.mobile.movirt.rest.Vm vm) {
//        Vm.Status status = Vm.Status.valueOf(vm.status.state.toUpperCase());
//        runButton.setClickable(Vm.Command.RUN.canExecute(status));
//        stopButton.setClickable(Vm.Command.POWEROFF.canExecute(status));
//        rebootButton.setClickable(Vm.Command.REBOOT.canExecute(status));
//    }

    @Background
    void loadAdditionalVmData(final Vm vm) {
        showProgressBar();

        client.getVm(vmId, new OVirtClient.SimpleResponse<ExtendedVm>() {
            @Override
            public void onResponse(final ExtendedVm loadedVm) throws RemoteException {
                client.getVmStatistics(vm, new OVirtClient.SimpleResponse<VmStatistics>() {
                    @Override
                    public void onResponse(VmStatistics vmStatistics) throws RemoteException {
                        hideProgressBar();

                        renderVm(loadedVm, vmStatistics);
                    }
                });
            }

            @Override
            public void onError() {
                super.onError();

                hideProgressBar();
            }
        });

    }

    @Override
    public void onPause() {
        super.onPause();
    }


}
