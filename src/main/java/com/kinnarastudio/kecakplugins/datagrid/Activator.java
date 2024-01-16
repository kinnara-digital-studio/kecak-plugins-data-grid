package com.kinnarastudio.kecakplugins.datagrid;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();
 
        //Register plugin here
        registrationList.add(context.registerService(JsonFormBinder.class.getName(), new JsonFormBinder(), null));
        registrationList.add(context.registerService(DataGridBinder.class.getName(), new DataGridBinder(), null));
        registrationList.add(context.registerService(DataGrid.class.getName(), new DataGrid(), null));
        registrationList.add(context.registerService(ForeignKeyElement.class.getName(), new ForeignKeyElement(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}