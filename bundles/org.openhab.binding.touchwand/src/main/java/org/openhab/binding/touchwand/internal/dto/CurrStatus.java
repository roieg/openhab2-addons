/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.binding.touchwand.internal.dto;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * The {@link CurrStatus} implements CurrStatus data class.
 *
 * @author Roie Geron - Initial contribution
 */

public class CurrStatus {

    @SerializedName("csc")
    @Expose
    private Csc csc;

    public Csc getCsc() {
        return csc;
    }

    public void setCsc(Csc csc) {
        this.csc = csc;
    }
}
