/*
 * AUTHOR:
 * Mark Tsuchida
 *
 * Copyright (c) 2013-2014 Regents of the University of California
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

package org.micromanager.diagnostics;

class CorePropertyCacheInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "Core information"; }

   public String getReport() {
      mmcorej.CMMCore c = org.micromanager.MMStudio.getInstance().getMMCore();

      StringBuilder sb = new StringBuilder();
      sb.append("Core property cache (\"system state cache\") contents:\n");
      final mmcorej.Configuration cachedProps = c.getSystemStateCache();
      long count = cachedProps.size();
      for (long i = 0; i < count; i++) {
         try {
            final mmcorej.PropertySetting s = cachedProps.getSetting(i);
            final String device = s.getDeviceLabel();
            final String name = s.getPropertyName();
            final String value = s.getPropertyValue();
            final String roString = s.getReadOnly() ? " [RO]" : "";
            sb.append("  ").append(device).append("/").append(name).
                    append(roString).append(" = ").append(value).append('\n');
         }
         catch (Exception e) {
            sb.append("  Error while getting cache item ").
                    append(Long.toString(i)).append(": ").
                    append(e.getMessage()).append('\n');
         }
      }
      return sb.toString();
   }
}
