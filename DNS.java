package profile;

import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Vector;

import core.iface.IUnit;
import core.model.NetworkModel;
import core.profile.AStructuredProfile;
import core.unit.fs.FileUnit;
import core.unit.pkg.InstalledUnit;
import core.unit.pkg.RunningUnit;

public class DNS extends AStructuredProfile {

	private Vector<String> gateways;
	private HashMap<String, Vector<String>> domainRecords;
	
	public DNS() {
		super("dns");
		
		domainRecords = new HashMap<String, Vector<String>>();
		gateways      = new Vector<String>();
	}

	private void addGateway(String ip) {
		if (!gateways.contains(ip) ) {
			gateways.add(ip);
		}
	}
	
	public void addDomainRecord(String domain, String gatewayIp, String[] subdomains, String ip) {
		this.addGateway(gatewayIp);
		
		Vector<String> records = domainRecords.get(domain);
		
		if (records == null) {
			domainRecords.put(domain, new Vector<String>());
		}
		records = domainRecords.get(domain);
		
		records.addElement("    local-data: \\\"" + subdomains[0] + " A " + ip + "\\\"");
		records.addElement("    local-data: \\\"" + subdomains[0] + "." + domain + " A " + ip + "\\\"");
		records.addElement("    local-data-ptr: \\\"" + ip + " " + subdomains[0] + "." + domain + "\\\"");
		records.addElement("    local-data-ptr: \\\"" + gatewayIp + " router." + subdomains[0] + "." + domain + "\\\"");

		for (String subdomain : subdomains) {
			//If you're trying to have a cname which is just the domain, it craps out unless you do this...
			if (!subdomain.equals("")) {
				records.addElement("    local-data: \\\"" + subdomain + " A " + ip + "\\\"");
				records.addElement("    local-data: \\\"" + subdomain + "." + domain + " A " + ip + "\\\"");
			}
			else {
				records.addElement("    local-data: \\\"" + domain + " A " + ip + "\\\"");
			}
		}
		
		domainRecords.put(domain,  records);
	}
	
	public Vector<IUnit> getPersistentConfig(String server, NetworkModel model) {
		Vector<IUnit> units = new Vector<IUnit>();
		
		//Config taken from https://calomel.org/unbound_dns.html
		String config = "";
		config += "server:\n";
		config += "    verbosity: 1\n"; //Log verbosity
		config += "    include: \\\"/etc/unbound/unbound.conf.d/interfaces.conf\\\"\n";
		config += "    port: 53\n";
		config += "    do-ip4: yes\n";
		config += "    do-ip6: no\n";
		config += "    do-udp: yes\n";
		config += "    do-tcp: yes\n";
		config += "    access-control: 10.0.0.0/0 allow\n";
		config += "    access-control: 127.0.0.0/0 allow\n";
		config += "    access-control: 192.168.0.0/0 allow\n";
		config += "    access-control: 172.16.0.0/0 allow\n";
		config += "    access-control: 127.0.0.1 allow\n";
		config += "    hide-identity: yes\n";
		config += "    hide-version: yes\n";
		config += "    harden-glue: yes\n";
		config += "    harden-dnssec-stripped: yes\n";
		config += "    use-caps-for-id: yes\n";
		config += "    cache-min-ttl: 3600\n";
		config += "    cache-max-ttl: 86400\n";
		config += "    prefetch: yes\n";
		config += "    num-threads: " + model.getData().getCpus(server) + "\n";
		config += "    msg-cache-slabs: " + (Integer.parseInt(model.getData().getCpus(server))*2) + "\n";
		config += "    rrset-cache-slabs: " + (Integer.parseInt(model.getData().getCpus(server))*2) + "\n";
		config += "    infra-cache-slabs: " + (Integer.parseInt(model.getData().getCpus(server))*2) + "\n";
		config += "    key-cache-slabs: " + (Integer.parseInt(model.getData().getCpus(server))*2) + "\n";
		config += "    rrset-cache-size: " + (Integer.parseInt(model.getData().getRam(server))/4) + "m\n";
		config += "    msg-cache-size: " + (Integer.parseInt(model.getData().getRam(server))/8) + "m\n";
		config += "    so-rcvbuf: 1m\n";
		config += "    private-address: 192.168.0.0/0\n";
		config += "    private-address: 172.16.0.0/0\n";
		config += "    private-address: 10.0.0.0/0\n";
		for (String domain : domainRecords.keySet()) {
			config += "    private-domain: \\\"" + domain + "\\\"\n";
		}
		config += "    unwanted-reply-threshold: 10000\n";
		config += "    do-not-query-localhost: yes\n";
		config += "    val-clean-additional: yes\n";

		if (model.getData().getAdBlocking()) {
			config += "    local-zone: \\\"doubleclick.net\\\" redirect\n";
			config += "    local-data: \\\"doubleclick.net A 127.0.0.1\\\"\n";
			config += "    local-zone: \\\"googlesyndication.com\\\" redirect\n";
			config += "    local-data: \\\"googlesyndication.com A 127.0.0.1\\\"\n";
			config += "    local-zone: \\\"googleadservices.com\\\" redirect\n";
			config += "    local-data: \\\"googleadservices.com A 127.0.0.1\\\"\n";
			config += "    local-zone: \\\"google-analytics.com\\\" redirect\n";
			config += "    local-data: \\\"google-analytics.com A 127.0.0.1\\\"\n";
			config += "    local-zone: \\\"ads.youtube.com\\\" redirect\n";
			config += "    local-data: \\\"ads.youtube.com A 127.0.0.1\\\"\n";
			config += "    local-zone: \\\"adserver.yahoo.com\\\" redirect\n";
			config += "    local-data: \\\"adserver.yahoo.com A 127.0.0.1\\\"\n";
			config += "    local-zone: \\\"ask.com\\\" redirect\n";
			config += "    local-data: \\\"ask.com A 127.0.0.1\\\"\n";
		}
		for (String domain : domainRecords.keySet()) {
			config += "    include: \\\"/etc/unbound/unbound.conf.d/" + domain + ".zone\\\"\n";
		}
		//rDNS
		config += "    local-zone: \\\"" + model.getServerModel(server).getGateway().split("\\.")[0] + ".in-addr.arpa.\\\" nodefault\n";
		config += "    stub-zone:\n";
		config += "        name: \\\"" + model.getServerModel(server).getGateway().split("\\.")[0] + ".in-addr.arpa.\\\"\n";
		config += "        stub-addr: " + model.getServerModel(server).getGateway() + "\n";
		config += "    forward-zone:\n";
		config += "        name: \\\".\\\"\n";
		config += "        forward-addr: " + model.getData().getDNS();
		
		units.addElement(new FileUnit("dns_persistent_config", "dns_installed", config, "/etc/unbound/unbound.conf"));
		
		return units;
	}

	protected Vector<IUnit> getInstalled(String server, NetworkModel model) {
		Vector<IUnit> units = new Vector<IUnit>();
		
		units.addElement(new InstalledUnit("dns", "unbound"));

		model.getServerModel(server).getUserModel().addUsername("unbound");
		model.getServerModel(server).getProcessModel().addProcess("/usr/sbin/unbound -d$");

		return units;
	}
	
	protected Vector<IUnit> getPersistentFirewall(String server, NetworkModel model) {
		Vector<IUnit> units = new Vector<IUnit>();
		
		units.addElement(model.getServerModel(server).getFirewallModel().addFilterInput("dns_ipt_in_udp",
				"-p udp --dport 53 -j ACCEPT"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilterOutput("dns_ipt_out_udp",
				"-p udp --sport 53 -j ACCEPT"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilterInput("dns_ipt_in_tcp",
				"-p tcp --dport 53 -j ACCEPT"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilterOutput("dns_ipt_out_tcp",
				"-p tcp --sport 53 -j ACCEPT"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilterOutput("dns_ipt_out_tcp_lo",
				"-p tcp --dport 53 -j ACCEPT"));
		units.addElement(model.getServerModel(server).getFirewallModel().addChain("dns_ipt_chain", "filter", "dnsd"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilter("dns_ext", "dnsd", "-j DROP"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilter("dns_ext_log", "dnsd",
				"-j LOG --log-prefix \\\"ipt-dnsd: \\\""));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilterInput("dns_ext_in",
				"-p udp --sport 53 -j dnsd"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilterOutput("dns_ext_out",
				"-p udp --sport 53 -j dnsd"));
		
		int count = 1;
		StringTokenizer str = new StringTokenizer(model.getData().getDNS());
		while (str.hasMoreTokens()) {
			String ip = str.nextToken(";");
			units.addElement(model.getServerModel(server).getFirewallModel().addFilter("dns_ext_server_in_" + count,
					"dnsd", "-s " + ip + " -p udp --sport 53 -j ACCEPT"));
			units.addElement(model.getServerModel(server).getFirewallModel().addFilter("dns_ext_server_out_" + count,
					"dnsd", "-d " + ip + " -p udp --dport 53 -j ACCEPT"));
			count++;
		}

		units.addElement(model.getServerModel(server).getFirewallModel().addFilter("dns_allow_loopback_in",
				"dnsd", "-i lo -j ACCEPT"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilter("dns_allow_loopback_out",
				"dnsd", "-o lo -j ACCEPT"));
		units.addElement(model.getServerModel(server).getFirewallModel().addFilter("dns_allow_bridges_in",
						"dnsd", "-i br+ -j ACCEPT"));
				units.addElement(model.getServerModel(server).getFirewallModel().addFilter("dns_allow_bridges_out",
						"dnsd", "-o br+ -j ACCEPT"));
		
		return units;
	}

	protected Vector<IUnit> getLiveConfig(String server, NetworkModel model) {
		Vector<IUnit> units = new Vector<IUnit>();
		
		units.addElement(new RunningUnit("dns", "unbound", "unbound"));
		
		String ifaceConfig = "";
		for (String gateway : this.gateways) {
			ifaceConfig += "    interface: " + gateway + "\n";
		}
		
		units.addElement(new FileUnit("dns_listening_interfaces", "dns_installed", ifaceConfig.replaceAll("\\s+$", ""), "/etc/unbound/unbound.conf.d/interfaces.conf"));

		for (String domain : domainRecords.keySet()) {
			String zoneConfig = "";
			zoneConfig += "    local-zone: \\\"" + domain + ".\\\" transparent";
	
			Vector<String> records = domainRecords.get(domain);
			
			for (String record : records) {
				zoneConfig += "\n";
				zoneConfig += record;
			}
			
			units.addElement(new FileUnit(domain.replaceAll("\\.", "_").replaceAll("-",  "_") + "_dns_internal_zone", "dns_installed", zoneConfig.replaceAll("\\s+$", ""), "/etc/unbound/unbound.conf.d/" + domain + ".zone"));
		}
		
		return units;
	}

}
