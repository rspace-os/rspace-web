package com.researchspace.model.permissions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.ListUtils;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.permission.PermissionResolver;

/**
 * Converts String based representation of a Permission to a Permissions object.
 */
public class ConstraintPermissionResolver implements PermissionResolver {

	private static final int MAX_ID_PERMS = 124;

	public ConstraintPermissionResolver() {
	}

	static final String DATE_RANGE_PARAM_PREFIX = "date_range";
	static final String CONSTRAINT_SEPARATOR = "&";
	static final String LIST_SEPARATOR = ",";
	static final String LOCATION_PARAM_PREFIX = "location";
	static final String PROPERTY_PARAM_PREFIX = "property_";
	static final String GROUP_PARAM_PREFIX = "group";
	static final String COMMUNITY_PARAM_PREFIX = "community";
	static final String IDS_PREFIX = "id";

	static final String PART_DELIMITER = ":";

	static final ConstraintPermissionResolver STATIC_RESOLVER = new ConstraintPermissionResolver();

	/**
	 * Takes an empty Permission object and builds it from the supplied String
	 * 
	 * @param p
	 * @param permStr
	 * @return
	 */
	public static ConstraintBasedPermission populatePermssion(ConstraintBasedPermission p, String permStr) {
		ConstraintBasedPermission copy = (ConstraintBasedPermission) STATIC_RESOLVER.resolvePermission(permStr);
		p.setDomain(copy.getDomain());
		p.setActions(copy.getActions());
		p.setIdConstraint(copy.getIdConstraint());
		p.setLocationConstraints(copy.getLocationConstraints());
		p.setPropertyConstraints(copy.getPropertyConstraints());
		p.setGroupConstraint(copy.getGroupConstraint());
		p.setCommunityConstraint(copy.getCommunityConstraint());
		return p;
	}

	@Override
	public ConstraintBasedPermission resolvePermission(String permissionString) {
		String[] parts = permissionString.trim().split(PART_DELIMITER);

		PermissionDomain domain = PermissionDomain.valueOf(parts[0].toUpperCase());

		String[] actions = parts[1].split(LIST_SEPARATOR);
		Set<PermissionType> actionsSet = new HashSet<>();
		for (int i = 0; i < actions.length; i++) {
			if (actions[i] != null && !actions[i].isEmpty()) {
				PermissionType pt = PermissionType.valueOf(actions[i].toUpperCase());
				actionsSet.add(pt);
			}
		}
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(domain, actionsSet);
		if (hasConstraintsDefined(parts)) {
			String[] constraints = parts[2].split(CONSTRAINT_SEPARATOR);
			for (String constraint : constraints) {
				if (constraint.startsWith(LOCATION_PARAM_PREFIX)) {
					LocationConstraint lc = parseLocationConstraint(constraint);
					cbp.addLocationConstraint(lc);
				} else if (constraint.startsWith(IDS_PREFIX)) {
					IdConstraint idconstraint = parseIdConstraint(constraint);
					cbp.setIdConstraint(idconstraint);
				} else if (constraint.startsWith(GROUP_PARAM_PREFIX)) {
					GroupConstraint grpconstraint = parseGroupConstraint(constraint);
					cbp.setGroupConstraint(grpconstraint);
				} else if (constraint.startsWith(COMMUNITY_PARAM_PREFIX)) {
					CommunityConstraint communityConstraint = parseCommunityConstraint(constraint);
					cbp.setCommunityConstraint(communityConstraint);
				}

				else if (constraint.startsWith(PROPERTY_PARAM_PREFIX)) {
					PropertyConstraint pc = parsePropertyConstraint(constraint);
					cbp.addPropertyConstraint(pc);
					pc.setOwner(cbp);
				}
			}

		}

		return cbp;
	}

	private GroupConstraint parseGroupConstraint(String constraint) {
		String grp = constraint.split("=")[1];
		return new GroupConstraint(grp);
	}

	private CommunityConstraint parseCommunityConstraint(String constraint) {
		String grp = constraint.split("=")[1];
		return new CommunityConstraint(Long.parseLong(grp));
	}

	public boolean hasConstraintsDefined(String[] parts) {
		return parts.length == 3;
	}

	public PropertyConstraint parsePropertyConstraint(String constraint) {
		String pName = constraint.substring(PROPERTY_PARAM_PREFIX.length(), constraint.indexOf('='));
		String pVal = constraint.split("=")[1];
		PropertyConstraint pc = new PropertyConstraint(pName, pVal);
		return pc;
	}

	public LocationConstraint parseLocationConstraint(String constraint) {
		String value = constraint.split("=")[1];
		LocationConstraint lc = new LocationConstraint(value);
		return lc;
	}

	public IdConstraint parseIdConstraint(String constraint) {
		String value = constraint.split("=")[1];
		String[] ids = value.split(LIST_SEPARATOR);
		Set<Long> idSet = new HashSet<>(ids.length);
		for (String id : ids) {
			idSet.add(Long.parseLong(id));
		}
		IdConstraint lc = new IdConstraint(idSet);
		return lc;
	}

	static final Pattern SINGLE_READ_PERM = Pattern.compile("RECORD:READ:id=(\\d+)");
	static final Pattern SINGLE_WRITE_PERM = Pattern.compile("RECORD:WRITE:id=(\\d+)");

	/**
	 * Modifies in place a collection of permissions, reducing many individual RECORD:READ:1, RECORD:READ:2 permissions
	 *  to  as few RECORD:READ:123,456,789 permissions as possible.
	 * Currently DB length for permissions is 2500. Max char length for a long is 19 characters; we can guarantee
	 *  to fit 124 ids + commas + 'RECORD:WRITE' into 2500 chars
	 */
	public void flattenRecordReadWritePermissions(Collection<Permission> allPerms) {
		List<Long> readids = new ArrayList<>();
		List<Long> writeids = new ArrayList<>();
		Iterator<Permission> it = allPerms.iterator();
		while (it.hasNext()) {
			Permission cbp = it.next();
			handleMatch(readids, it, cbp, SINGLE_READ_PERM);
			handleMatch(writeids, it, cbp, SINGLE_WRITE_PERM);
		}
		flatten(readids, PermissionType.READ, allPerms);
		flatten(writeids, PermissionType.WRITE, allPerms);

	}

	private void handleMatch(List<Long> ids, Iterator<Permission> it, Permission cbp, Pattern pattern) {
		Matcher m = pattern.matcher(cbp.toString());
		if (m.matches()) {
			ids.add(Long.parseLong(m.group(1)));
			it.remove();
		}
	}

	private void flatten(List<Long> ids, PermissionType permType, Collection<Permission> allPerms) {
		if (!ids.isEmpty()) {
			List<List<Long>> partitioned = ListUtils.partition(ids, MAX_ID_PERMS);
			for (List<Long> pt : partitioned) {
				ConstraintBasedPermission flattenedReads = new ConstraintBasedPermission(PermissionDomain.RECORD,
						permType);
				IdConstraint id = new IdConstraint(new HashSet<>(pt));
				flattenedReads.setIdConstraint(id);

				allPerms.add(flattenedReads);
			}
		}
	}

}
