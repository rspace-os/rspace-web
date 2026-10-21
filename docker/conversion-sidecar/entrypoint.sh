#!/bin/sh
set -eu

# Resolve the fixed peer before disabling DNS and all other outbound connections.
# Recreate the pair together if the Gotenberg container's address changes.
peer=$(getent ahostsv4 gotenberg | awk 'NR == 1 { print $1 }')
[ -n "$peer" ] || { echo 'Paired Gotenberg is not resolvable' >&2; exit 1; }
cat /etc/hosts > /tmp/conversion-hosts
printf '%s gotenberg\n' "$peer" >> /tmp/conversion-hosts

iptables -P OUTPUT DROP
iptables -F OUTPUT
# Ahead of the conntrack rule: the startup lookup's UDP flow would otherwise stay
# reusable as ESTABLISHED. 127.0.0.11 is Docker's embedded resolver (post-DNAT).
iptables -A OUTPUT -d 127.0.0.11/32 -j DROP
iptables -A OUTPUT -p udp --dport 53 -j DROP
iptables -A OUTPUT -p tcp --dport 53 -j DROP
iptables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
iptables -A OUTPUT -d 127.0.0.1/32 -j ACCEPT
iptables -A OUTPUT -d "$peer/32" -p tcp --dport 3000 -j ACCEPT
ip6tables -P OUTPUT DROP
ip6tables -F OUTPUT
ip6tables -A OUTPUT -p udp --dport 53 -j DROP
ip6tables -A OUTPUT -p tcp --dport 53 -j DROP
ip6tables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
ip6tables -A OUTPUT -d ::1/128 -j ACCEPT

# Java and its children cannot change the firewall or recover startup privileges.
exec setpriv --reuid=10001 --regid=10001 --clear-groups \
    --bounding-set=-all --inh-caps=-all --ambient-caps=-all --no-new-privs \
    /usr/bin/tini -s -- java -Djdk.net.hosts.file=/tmp/conversion-hosts -jar /opt/conversion-sidecar.jar
