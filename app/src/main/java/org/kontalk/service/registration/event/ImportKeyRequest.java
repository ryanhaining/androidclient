/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service.registration.event;


import org.kontalk.client.EndpointServer;

/**
 * Import personal key request event.
 * @author Daniele Ricci
 */
public class ImportKeyRequest {

    public final EndpointServer server;
    public final byte[] privateKeyData;
    public final byte[] publicKeyData;
    public final String passphrase;

    public ImportKeyRequest(EndpointServer server,
            byte[] privateKeyData, byte[] publicKeyData, String passphrase) {
        this.server = server;
        this.privateKeyData = privateKeyData;
        this.publicKeyData = publicKeyData;
        this.passphrase = passphrase;
    }

}
