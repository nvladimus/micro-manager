/*
 * AUTHOR:
 * Arthur Edelstein
 *
 * Copyright (c) 2010 Regents of the University of California
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

package org.micromanager.api;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.micromanager.events.EventManager;
import org.micromanager.events.ProcessorEnabledEvent;
import org.micromanager.utils.ReportingUtils;

/**
 * A DataProcessor thread allows for on-the-fly modification of image
 * data during acquisition.
 *
 * Inherit from this class and use the AcquisitionEngine functions
 * addImageProcessor and removeImageProcessor to insert your code into the
 * acquisition pipeline
 * 
 * Note that this code will be invoked through its default (null) constructor
 * If you use your own constructor, make sure that it accepts no arguments.
 * If you need to initialize variables, do this in the makeConfigurationGUI 
 * method instead.  That method is the first to be called.
 */
public abstract class DataProcessor<E> extends Thread {
   private BlockingQueue<E> input_;
   private BlockingQueue<E> output_;
   private boolean stopRequested_ = false;
   private boolean started_ = false;
   // This boolean controls whether or not this DataProcessor will receive
   // images.
   private boolean isEnabled_ = true;

   /**
    * The scripting interface (commonly known as the "gui" object).
    */
   protected ScriptInterface gui_;

   /**
    * The process method should be overridden by classes implementing
    * DataProcessor, to provide a processing function.
    * 
    * For example, an "Identity" DataProcessor (where nothing is
    * done to the data) would override process() thus:
    *
    * <pre><code>
    * @Override
    * public void process() {
    *    produce(poll());
    * }
    * </code></pre>
    *
    * TaggedImageQueue.POISON will be the last object
    * received by polling -- the process method should pass this
    * object on unchanged.
    */
   protected abstract void process();

   /** 
    * Generate and show the GUI needed to configure the DataProcessor. 
    */
   public void makeConfigurationGUI() {};

   /**
    * Remove all GUI elements generated by makeConfigurationGUI(). If you
    * override makeConfigurationGUI(), then you should also override this
    * function. 
    */
   public void dispose() {};

   /**
    * Receive the ScriptInterface object.
    *
    * Normally, it is not necessary to override this method. If overriding,
    * make sure to call super.setApp().
    */
   public void setApp(ScriptInterface gui) {
      gui_ = gui;
   }

   /**
    * The run method that causes images to be processed. As DataProcessor
    * extends <code>java.lang.Thread</code>, this method will be executed
    * whenever DataProcessor.start() is called.
    *
    * Do not override this method (it should have been final).
    */
   @Override
   public void run() {
      setStarted(true);
      while (!stopRequested_) {
         process();
      }
   }

   /**
    * Request that the data processor stop processing. The current
    * processing event will continue, but no others will be started.
    *
    * Do not override this method (it should have been final). Do not call
    * this method from DataProcessor subclasses.
    */
   public synchronized void requestStop() {
      stopRequested_ = true;
   }

   /*
    * Private method for tracking when processing has started.
    */
   private synchronized void setStarted(boolean started) {
      started_ = started;
   }

   /**
    * Returns true if the DataProcessor has started up and objects
    * are being processed as they arrive.
    *
    * Do not override this method (it should have been final).
    */
   public synchronized boolean isStarted() {
      return started_;
   }

   /**
    * Sets the input queue where objects to be processed
    * are received by the DataProcessor.
    *
    * Do not override this method (it should have been final). This method is
    * automatically called by the system to set up data processors.
    */
   public synchronized void setInput(BlockingQueue<E> input) {
      input_ = input;
   }

   /**
    * Sets the output queue where objects that have been processed
    * exit the DataProcessor.
    *
    * Do not override this method (it should have been final). This methods is
    * automatically called by the system to set up data processors.
    */
   public void setOutput(BlockingQueue<E> output) {
      output_ = output;
   }

   /**
    * A protected method that reads the next object from the input
    * queue.
    *
    * This is the method that process() implementations should call to
    * receive the image to process.
    *
    * Do not override this method (it should have been final).
    */
   protected E poll() {
      while (!stopRequested()) {
         try {
            // Ensure that input_ doesn't change between checking nullness
            // and polling.
            BlockingQueue<E> tmpQueue;
            synchronized(this) {
               tmpQueue = input_;
            }
            if (tmpQueue != null) {
               E datum = tmpQueue.poll(100, TimeUnit.MILLISECONDS);
               if (datum != null) {
                  return datum;
               }
            }
            if (tmpQueue == null) {
               // Sleep to avoid busywaiting.
               Thread.sleep(100);
            }
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         }
      }
      return null;
   }

   /**
    * A convenience method for draining all available data objects
    * on the input queue to a collection.
    *
    * Do not override this method (it should have been final).
    */
   protected void drainTo(Collection<E> data) {
      input_.drainTo(data);
   }

   /**
    * A convenience method for posting a data object to the output queue.
    *
    * This is the method that process() implementations should call to
    * send out the processed image(s).
    *
    * Do not override this method (it should have been final).
    */
   protected void produce(E datum) {
      try {
         output_.put(datum);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }
   };

   /**
    * Returns true if stop has been requested.
    *
    * Usually, subclasses need not care about stop requests, as they are
    * handled automatically.
    */
   protected synchronized boolean stopRequested() {
      return stopRequested_;
   }

   /**
    * Turn the Processor on or off.
    *
    * Enabling and disabling the processor is handled automatically by the
    * system.
    *
    * It is usually not necessary to override this method. If overriding, make
    * sure to call super.setEnabled(isEnabled).
    */
   public void setEnabled(boolean isEnabled) {
      if (isEnabled_ == isEnabled) {
         return;
      }

      final boolean liveWasOn = gui_.isLiveModeOn();
      if (liveWasOn) {
         gui_.enableLiveMode(false);
      }

      isEnabled_ = isEnabled;
      EventManager.post(new ProcessorEnabledEvent(this, isEnabled));

      if (liveWasOn) {
         gui_.enableLiveMode(true);
      }
   }

   /**
    * Get whether or not this Processor is enabled.
    *
    * Do not override this method (it should have been final).
    */
   public boolean getIsEnabled() {
      return isEnabled_;
   }
}
