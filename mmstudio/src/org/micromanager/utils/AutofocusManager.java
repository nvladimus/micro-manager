/*
 * AUTHOR:
 * Nenad Amodaj, nenad@amodaj.com, May 2009
 *
 * Copyright (c) 2009 100X Imaging Inc
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.micromanager.utils;

import java.util.Vector;


import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.api.Autofocus;
import org.micromanager.api.ScriptInterface;

/**
 * Manages different instances of autofocus devices, both Java plugin and MMCore based.
 * The class is designed to be instantiated in the top level gui and used to obtain
 * the list of available focusing devices, as well as for selecting a default one.
 */
public class AutofocusManager {
   private ScriptInterface app_;
   private Vector<Autofocus> afs_;
   private Vector<String> afPluginClassNames_;
   private Autofocus currentAfDevice_;
   private AutofocusPropertyEditor afDlg_;
   
   public AutofocusManager(ScriptInterface app) {
      afs_ = new Vector<Autofocus>();
      afPluginClassNames_ = new Vector<String>();
      currentAfDevice_ = null;
      app_ = app;
   }
   
   /**
    * Selects a default autofocus device.
    * @param name - device name
    * @throws MMException
    */
   public void selectDevice(String name) throws MMException {
      for (Autofocus af : afs_) {
         if (af.getDeviceName().equals(name)) {
            currentAfDevice_ = af;
            return;
         }
      }
      
      throw new MMException(name + " not loaded.");
   }
   
   /**
    * Sets a class name for a Java af plugin.

    * TODO: add multiple plugin devices 
    * @param className - plugin class name
    */
   public void setAFPluginClassName(String className) {
	   if (! afPluginClassNames_.contains(className))
		   afPluginClassNames_.add(className);
   }

   /**
    * Returns the current af device or null if none loaded.
    * Callers should always use this method to obtain the current af device instead
    * of storing the af device reference directly.
    * @return - current AutoFocus device or null if none was loaded
    */
   public Autofocus getDevice() {
      return currentAfDevice_;
   }
   
   /**
    * Scans the system for available af devices, both plugin and core based
    * If it has a current AFDevice, try to keep the same device as the current one
    * Update the Autofcosu property dialog
    * @throws MMException 
    */
   public void refresh() throws MMException {
      afs_.clear();
      CMMCore core = app_.getMMCore();

      // first check core autofocus
      StrVector afDevs = core.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
      for (int i=0; i<afDevs.size(); i++) {
         CoreAutofocus caf = new CoreAutofocus();
         try {
            core.setAutoFocusDevice(afDevs.get(i));
            caf.setApp(app_);
            if (caf.getDeviceName().length() != 0) {
               afs_.add(caf);
               if (currentAfDevice_ == null)
                  currentAfDevice_ = caf;
            }
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }

      // then check Java
      try {

    	  for (int i=0; i<afPluginClassNames_.size(); i++) {
    		  String name = afPluginClassNames_.get(i);
    		  if (name.length() != 0) {

    			  Autofocus jaf = null;
    			  try {
    				  jaf = loadAutofocusPlugin(name);
    			  } catch (Exception e) {
                      ReportingUtils.logError(e);
    				  afPluginClassNames_.remove(name);
    				  i--;
    			  }

    			  if (jaf != null) {
    				  afs_.add(jaf);
    				  if (currentAfDevice_ == null)
    					  currentAfDevice_ = jaf;
    				  jaf.setApp(app_);
    			  }
    		  }
    	  }
      } catch (Exception e) {
    	  ReportingUtils.logError(e);
      }

      // make sure the current autofocus is still in the list, otherwise set it to something...
      boolean found = false;
      for (Autofocus af : afs_) {
         if (af.getDeviceName().equals(currentAfDevice_.getDeviceName())) {
            found = true;
            currentAfDevice_ = af;
         }
      }
      if (!found && afs_.size() > 0)
         currentAfDevice_ = afs_.get(0);
  
      // Show new list in Options Dialog
      if (afDlg_ != null) 
         afDlg_.rebuild();

   }
      
   public void showOptionsDialog() {
      if (afDlg_ == null)
         afDlg_ = new AutofocusPropertyEditor(this);
      afDlg_.setVisible(true);
      if (currentAfDevice_ != null) {
         currentAfDevice_.applySettings();
         currentAfDevice_.saveSettings();
      }
   }

   public void closeOptionsDialog() {
      if (afDlg_ != null)
         afDlg_.cleanup();
   }

   /**
    * Returns a list of available af device names
    * @return - array of af names
    */
   public String[] getAfDevices() {
      String afDevs[] = new String[afs_.size()];
      int count = 0;
      for (Autofocus af : afs_) {
         afDevs[count++] = af.getDeviceName();
      }
      return afDevs;
   }

   @SuppressWarnings("unchecked")
   private Autofocus loadAutofocusPlugin(String className) throws MMException {
      String msg = new String(className + " module.");
      // instantiate auto-focusing module
      Autofocus af = null;
      try {
         Class cl = Class.forName(className);
         af = (Autofocus) cl.newInstance();
         return af;
      } catch (ClassNotFoundException e) {
         ReportingUtils.logError(e);
         msg = className + " autofocus plugin not found.";
      } catch (InstantiationException e) {
          ReportingUtils.logError(e);
          msg = className + " instantiation to Autofocus interface failed.";
      } catch (IllegalAccessException e) {
          ReportingUtils.logError(e);
          msg = "Illegal access exception!";
      } catch (NoClassDefFoundError e) {
          ReportingUtils.logError(e);
          msg = className + " class definition nor found.";
      }
      
      // not found
      ReportingUtils.logMessage(msg);
      throw new MMException(msg);
   }
   
   public boolean hasDevice(String dev) {
      for (Autofocus af : afs_) {
         if (af.getDeviceName().equals(dev))
            return true;
      }
      return false;
   }

}
