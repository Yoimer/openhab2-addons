/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ihc.ws.services;

import org.openhab.binding.ihc.ws.datatypes.WSLoginResult;
import org.openhab.binding.ihc.ws.exeptions.IhcExecption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle IHC / ELKO LS Controller's authentication service.
 *
 * Communication to controller need to be authenticated. On successful
 * authentication Controller returns session id which need to be used further
 * communication.
 *
 * @author Pauli Anttila - Initial contribution
 */
public class IhcAuthenticationService extends IhcBaseService {
    private static final Logger logger = LoggerFactory.getLogger(IhcAuthenticationService.class);

    IhcAuthenticationService(String host) {
        url = "https://" + host + "/ws/AuthenticationService";
    }

    public IhcAuthenticationService(String host, int timeout) {
        this(host);
        this.timeout = timeout;
        super.setConnectTimeout(timeout);
    }

    public WSLoginResult authenticate(String username, String password, String application) throws IhcExecption {
        logger.debug("Open connection");

        // @formatter:off
        final String soapQuery =
                  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + " <soapenv:Body>\n"
                + "  <authenticate1 xmlns=\"utcs\">\n"
                + "   <password>%s</password>\n"
                + "   <username>%s</username>\n"
                + "   <application>%s</application>\n"
                + "  </authenticate1>\n"
                + " </soapenv:Body>\n"
                + "</soapenv:Envelope>";
        // @formatter:on

        String query = String.format(soapQuery, password, username, application);
        openConnection(url);
        String response = sendQuery(query, timeout);
        return new WSLoginResult().parseXMLData(response);
    }
}
