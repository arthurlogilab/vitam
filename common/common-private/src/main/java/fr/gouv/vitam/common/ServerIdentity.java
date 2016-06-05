/**
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Pattern;

import fr.gouv.vitam.common.json.JsonHandler;

/**
 * Server Identity containing ServerName, ServerRole, Global PlatformId<br>
 * This is a private Common.<br>
 * <br>
 * By default this class is initialized with default values:<br>
 * <ul>
 * <li>ServerName: hostname or UnknownHostname if not found</li>
 * <li>ServerRole: UnknownRole</li>
 * <li>PlatformId: partial MAC ADDRESS as integer</li>
 * </ul>
 * <br>
 * One should initialize its server instance by calling:<br>
 *
 * <pre>
 * ServerIdentity serverIdentity = ServerIdentity.getInstance();
 * serverIdentity.setName(name).setRole(role).setPlatformId(platformId);
 * or
 * ServerIdentity.getInstance().setFromMap(map);
 * or
 * ServerIdentity.getInstance().setFromPropertyFile(file);
 * </pre>
 *
 * Where name, role and platformID comes from a configuration file for instance.<br>
 * <br>
 * Role could be a multiple, noted as role1_role2_role3. <br>
 * Usage:<br>
 *
 * <pre>
 * ServerIdentity serverIdentity = ServerIdentity.getInstance();
 * String name = serverIdentity.getName();
 * String role = serverIdentity.getRole();
 * int platformId = serverIdentity.getPlatformId();
 * </pre>
 *
 * Main usages are for:<br>
 * <ul>
 * <li>GUID for PlatformId</li>
 * <li>Logger and Logbook: for all</li>
 * </ul>
 */
public final class ServerIdentity implements ServerIdentityInterface {
    private static final int OTHER_ADDRESS = 4;
    private static final int SITE_LOCAL_ADDRESS = 3;
    private static final int LINKLOCAL_ADDRESS = 2;
    private static final int MULTICAST_ADDRESS = 1;
    private static final int LOCAL_ADDRESS = 0;
    private static final String UNKNOWN_ROLE = "UnknownRole";
    private static final String UNKNOWN_HOSTNAME = "UnknownHostname";
    private static final String HOSTNAME_CMD = "hostname";
    private static final String HOSTNAME = "HOSTNAME";
    private static final String COMPUTERNAME = "COMPUTERNAME";
    private static final String WIN = "win";
    private static final String OS_NAME = "os.name";
    /**
     * MAC ADDRESS support: support for default 6 and 8 bytes length, but also special 4 bytes
     */
    private static final Pattern MACHINE_ID_PATTERN = Pattern.compile("^(?:[0-9a-fA-F][:-]?){4,8}$");
    private static final int MACHINE_ID_LEN = 4;

    private static final ServerIdentity SERVER_IDENTITY = new ServerIdentity();
    private String name;
    private String role;
    private int platformId;
    private final StringBuilder preMessage = new StringBuilder();
    private String preMessageString;

    private ServerIdentity() {
        // Compute name from Hostname
        if (System.getProperty(OS_NAME).toLowerCase().startsWith(WIN)) {
            // Just for fun
            name = System.getenv(COMPUTERNAME);
        } else {
            name = System.getenv(HOSTNAME);
            if (name == null) {
                // Some Unix do return null
                Process proc;
                try {
                    proc = Runtime.getRuntime().exec(HOSTNAME_CMD);
                    try (InputStream stream = proc.getInputStream()) {
                        try (Scanner scanner = new Scanner(stream)) {
                            name = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                        }
                    }
                } catch (final IOException e) {//NOSONAR ignore
                    // ignore since will be checked just after
                }
                if (name == null) {
                    // Warning since it could return the real IP
                    try {
                        name = InetAddress.getLocalHost().getHostName();
                    } catch (final UnknownHostException e) {//NOSONAR ignore
                        name = UNKNOWN_HOSTNAME;
                    }
                }
            }
        }
        name = name.replaceAll("[\n\r]", "");
        role = UNKNOWN_ROLE;
        platformId = macAddress(macAddress());
        initializeCommentFormat();
    }

    /**
     * initialize after each configuration change the Logger pre-message
     */
    private void initializeCommentFormat() {
        preMessage.setLength(0);
        preMessage.append('[').append(getName()).append(':').append(getRole())
            .append(':').append(getPlatformId()).append("] ");
        preMessageString = preMessage.toString();
    }
    
    @Override
    public final String getLoggerMessagePrepend() {
        return preMessageString;
    }
    
    /**
     * 
     * @return the Json representation of the ServerIdentity
     */
    public final String getJsonIdentity() {
        return JsonHandler.createObjectNode().put("name", getName())
            .put("role", getRole()).put("pid", getPlatformId()).toString();
    }

    /**
     *
     * @return the current instance of ServerIdentity
     */
    public static final ServerIdentity getInstance() {
        return SERVER_IDENTITY;
    }

    /**
     * Map key for setFromMap
     */
    public enum MAP_KEYNAME {
        /**
         * Server Hostname: String
         */
        NAME,
        /**
         * Server Role: String
         */
        ROLE,
        /**
         * Global PlatformId: Integer or String representing integer
         */
        PLATFORMID;
    }

    /**
     * Assign the ServerIdentity from a Property file where Key are elements from MAP_KEYNAME. <br>
     * Other keys are ignored. Illegal values are ignored.
     *
     * @param propertiesFile file
     * @return this
     * @throws FileNotFoundException if the property file is not found
     */
    public final ServerIdentity setFromPropertyFile(File propertiesFile) throws FileNotFoundException {
        try {
            final Properties properties = PropertiesUtils.readProperties(propertiesFile);
            String svalue = properties.getProperty(MAP_KEYNAME.NAME.name());
            if (svalue != null) {
                name = svalue;
            }
            svalue = properties.getProperty(MAP_KEYNAME.ROLE.name());
            if (svalue != null) {
                role = svalue;
            }
            svalue = properties.getProperty(MAP_KEYNAME.PLATFORMID.name());
            platformId = Integer.parseInt(svalue);
        } catch (final IOException | NumberFormatException e) {//NOSONAR ignore
            // ignore
        }
        initializeCommentFormat();
        return this;
    }

    private final String getStringFromMap(Map<String, Object> map, String key) {
        final Object value = map.get(key);
        if (value != null && value instanceof String) {
            final String svalue = ((String) value).trim();
            if (!svalue.isEmpty()) {
                return svalue;
            }
        }
        return null;
    }

    private final Integer getIntegerFromMap(Map<String, Object> map, String key) {
        final Object value = map.get(key);
        if (value != null) {
            if (value instanceof String) {
                final String svalue = ((String) value).trim();
                if (!svalue.isEmpty()) {
                    try {
                        return Integer.parseInt(svalue);
                    } catch (final NumberFormatException e) {
                        return null;
                    }
                }
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }

    /**
     * Assign the ServerIdentity from a Map where Key are elements from MAP_KEYNAME. <br>
     * Other keys are ignored. Illegal values are ignored.
     *
     * @param map the map from which the values are to be set
     * @return this
     * @throws IllegalArgumentException map null
     */
    public final ServerIdentity setFromMap(Map<String, Object> map) {
        ParametersChecker.checkParameter("map", map);
        String svalue = getStringFromMap(map, MAP_KEYNAME.NAME.name());
        if (svalue != null) {
            name = svalue;
        }
        svalue = getStringFromMap(map, MAP_KEYNAME.ROLE.name());
        if (svalue != null) {
            role = svalue;
        }
        final Integer pid = getIntegerFromMap(map, MAP_KEYNAME.PLATFORMID.name());
        if (pid != null) {
            platformId = pid.intValue();
        }
        initializeCommentFormat();
        return this;
    }

    @Override
    public final String getName() {
        return name;
    }

    /**
     * @param name the name of the Server to set
     *
     * @return this
     * @throws IllegalArgumentException name null
     */
    public final ServerIdentity setName(String name) {
        ParametersChecker.checkParameter("Name", name);
        this.name = name;
        initializeCommentFormat();
        return this;
    }

    @Override
    public final String getRole() {
        return role;
    }

    /**
     * @param role the role of the Server to set
     *
     * @return this
     * @throws IllegalArgumentException role
     */
    public final ServerIdentity setRole(String role) {
        ParametersChecker.checkParameter("Role", role);
        this.role = role;
        initializeCommentFormat();
        return this;
    }

    @Override
    public final int getPlatformId() {
        return platformId;
    }

    /**
     * The PlatformId is a unique name per site (each of the 3 sites of Vitam should have a different id).
     *
     * @param platformId the platformId of the Vitam Platform to set
     *
     * @return this
     * @throws IllegalArgumentException platformId < 0
     */
    public final ServerIdentity setPlatformId(int platformId) {
        ParametersChecker.checkValue("platform", platformId, 0);
        this.platformId = platformId;
        initializeCommentFormat();
        return this;
    }


    /**
     *
     * @return the mac address if possible, else random values
     */
    private static final byte[] macAddress() {
        try {
            byte[] machineId = null;
            final String customMachineId =
                SystemPropertyUtil.get("fr.gouv.vitam.machineId");
            if (customMachineId != null && MACHINE_ID_PATTERN.matcher(customMachineId).matches()) {
                machineId = parseMachineId(customMachineId);
            }
            if (machineId == null) {
                machineId = defaultMachineId();
            }
            machineId[0] &= 0x7F;
            return machineId;
        } catch (final Exception e) {//NOSONAR ignore
            // Could not get MAC address: generate a random one
            final byte[] machineId = StringUtils.getRandom(MACHINE_ID_LEN);
            machineId[0] &= 0x7F;
            return machineId;
        }
    }

    private static final int macAddress(byte[] mac) {
        int macl = 0;
        if (mac == null) {
            return macl;
        }
        int i = mac.length - 4;
        if (i < 0) {
            i = 0;
        }
        macl |= (mac[i++] & 0x7F) << 3 * 8;
        for (int j = 1; i < mac.length; i++, j++) {
            macl |= (mac[i] & 0xFF) << (3 - j) * 8;
        }
        return macl;
    }

    private static final byte[] parseMachineId(final String valueSource) {
        // Strip separators.
        final String value = valueSource.replaceAll("[:-]", "");

        final byte[] machineId = new byte[4];
        int i = value.length() / 2;
        for (int j = 0; i < value.length() && j < 4; i += 2, j++) {
            machineId[j] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return machineId;
    }

    // Inspired from Netty MacAddressUtil
    /**
     * Obtains the best MAC address found on local network interfaces. Generally speaking, an active network interface
     * used on public networks is better than a local network interface.
     *
     * @return byte array containing a MAC. null if no MAC can be found.
     */
    private static final byte[] defaultMachineId() {
        final byte[] notFound = {-1};
        final byte[] localhost4Bytes = {127, 0, 0, 1};
        // Find the best MAC address available.
        byte[] bestMacAddr = notFound;
        InetAddress bestInetAddr = null;
        try {
            bestInetAddr = InetAddress.getByAddress(localhost4Bytes);
        } catch (final Exception e) {
            // Should not happen
            throw new IllegalArgumentException(e);
        }
        // Retrieve the list of available network interfaces.
        final Map<NetworkInterface, InetAddress> ifaces =
            new LinkedHashMap<>();
        try {
            for (final Enumeration<NetworkInterface> i =
                NetworkInterface.getNetworkInterfaces(); i.hasMoreElements();) {
                final NetworkInterface iface = i.nextElement();
                // Use the interface with proper INET addresses only.
                final Enumeration<InetAddress> addrs = iface.getInetAddresses();
                if (addrs.hasMoreElements()) {
                    final InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress()) {
                        ifaces.put(iface, a);
                    }
                }
            }
        } catch (final SocketException e) {
            // Should not happen
            throw new IllegalArgumentException(e);
        }
        for (final Entry<NetworkInterface, InetAddress> entry : ifaces.entrySet()) {
            final NetworkInterface iface = entry.getKey();
            final InetAddress inetAddr = entry.getValue();
            if (iface.isVirtual()) {
                continue;
            }

            final byte[] macAddr;
            try {
                macAddr = iface.getHardwareAddress();
            } catch (final SocketException e) {//NOSONAR ignore
                continue;
            }
            boolean replace = false;
            int res = compareAddresses(bestMacAddr, macAddr);
            if (res < 0) {
                // Found a better MAC address.
                replace = true;
            } else if (res == 0) {
                // Two MAC addresses are of pretty much same quality.
                res = compareAddresses(bestInetAddr, inetAddr);
                if (res < 0) {
                    // Found a MAC address with better INET address.
                    replace = true;
                } else if (res == 0 && bestMacAddr.length < macAddr.length) {
                    // Cannot tell the difference. Choose the longer one.
                    replace = true;
                }
            }
            if (replace) {
                bestMacAddr = macAddr;
                bestInetAddr = inetAddr;
            }
        }
        if (bestMacAddr == notFound) {
            bestMacAddr = StringUtils.getRandom(MACHINE_ID_LEN);
        }
        return bestMacAddr;
    }

    /**
     * @return positive - current is better, 0 - cannot tell from MAC addr, negative - candidate is better.
     */
    private static final int compareAddresses(final byte[] current,
        final byte[] candidate) {
        if (candidate == null) {
            return 1;
        }
        // Must be EUI-48 or longer.
        if (candidate.length < 6) {
            return 1;
        }
        // Must not be filled with only 0 and 1.
        boolean onlyZeroAndOne = true;
        for (final byte b : candidate) {
            if (b != 0 && b != 1) {
                onlyZeroAndOne = false;
                break;
            }
        }
        if (onlyZeroAndOne) {
            return 1;
        }
        // Must not be a multicast address
        if ((candidate[0] & 1) != 0) {
            return 1;
        }
        // Prefer globally unique address.
        if ((current[0] & 2) == 0) {
            if ((candidate[0] & 2) == 0) {
                // Both current and candidate are globally unique addresses.
                return 0;
            } else {
                // Only current is globally unique.
                return 1;
            }
        } else {
            if ((candidate[0] & 2) == 0) {
                // Only candidate is globally unique.
                return -1;
            } else {
                // Both current and candidate are non-unique.
                return 0;
            }
        }
    }

    /**
     * @return positive - current is better, 0 - cannot tell, negative - candidate is better
     */
    private static final int compareAddresses(final InetAddress current,
        final InetAddress candidate) {
        return scoreAddress(current) - scoreAddress(candidate);
    }

    /**
     *
     * @param addr
     * @return the score of this address
     */
    private static final int scoreAddress(final InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return LOCAL_ADDRESS;
        }
        if (addr.isMulticastAddress()) {
            return MULTICAST_ADDRESS;
        }
        if (addr.isLinkLocalAddress()) {
            return LINKLOCAL_ADDRESS;
        }
        if (addr.isSiteLocalAddress()) {
            return SITE_LOCAL_ADDRESS;
        }
        return OTHER_ADDRESS;
    }

}
