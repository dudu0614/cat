package com.dianping.cat.system.config;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.unidal.dal.jdbc.DalNotFoundException;
import org.unidal.helper.Files;
import org.unidal.lookup.annotation.Inject;

import com.dianping.cat.Cat;
import com.dianping.cat.core.config.Config;
import com.dianping.cat.core.config.ConfigDao;
import com.dianping.cat.core.config.ConfigEntity;
import com.dianping.cat.home.alert.policy.entity.AlertPolicy;
import com.dianping.cat.home.alert.policy.entity.Group;
import com.dianping.cat.home.alert.policy.entity.Level;
import com.dianping.cat.home.alert.policy.entity.Type;
import com.dianping.cat.home.alert.policy.transform.DefaultSaxParser;
import com.dianping.cat.report.task.alert.sender.AlertChannel;

public class AlertPolicyManager implements Initializable {

	@Inject
	private ConfigDao m_configDao;

	private int m_configId;

	private AlertPolicy m_config;

	private static final String CONFIG_NAME = "alertPolicy";

	private static final String DEFAULT_TYPE = "default";

	private static final String DEFAULT_GROUP = "default";

	public AlertPolicy getAlertPolicy() {
		return m_config;
	}

	@Override
	public void initialize() throws InitializationException {
		try {
			Config config = m_configDao.findByName(CONFIG_NAME, ConfigEntity.READSET_FULL);
			String content = config.getContent();

			m_configId = config.getId();
			m_config = DefaultSaxParser.parse(content);
		} catch (DalNotFoundException e) {
			try {
				String content = Files.forIO().readFrom(
				      this.getClass().getResourceAsStream("/config/default-alert-policy.xml"), "utf-8");
				Config config = m_configDao.createLocal();

				config.setName(CONFIG_NAME);
				config.setContent(content);
				m_configDao.insert(config);

				m_configId = config.getId();
				m_config = DefaultSaxParser.parse(content);
			} catch (Exception ex) {
				Cat.logError(ex);
			}
		} catch (Exception e) {
			Cat.logError(e);
		}
		if (m_config == null) {
			m_config = new AlertPolicy();
		}
	}

	public boolean insert(String xml) {
		try {
			m_config = DefaultSaxParser.parse(xml);

			return storeConfig();
		} catch (Exception e) {
			Cat.logError(e);
			return false;
		}
	}

	public List<AlertChannel> queryChannels(String typeName, String groupName, String levelName) {
		try {
			Level level = queryLevel(typeName, groupName, levelName);
			if (level == null) {
				return new ArrayList<AlertChannel>();
			} else {
				String send = level.getSend();
				String[] sends = send.split(",");
				List<AlertChannel> channels = new ArrayList<AlertChannel>();

				for (String str : sends) {
					AlertChannel channel = AlertChannel.findByName(str);

					if (channel != null) {
						channels.add(channel);
					}
				}

				return channels;
			}
		} catch (Exception ex) {
			return new ArrayList<AlertChannel>();
		}
	}

	private Level queryLevel(String typeName, String groupName, String levelName) {
		Type type = m_config.findType(typeName);
		if (type == null) {
			type = m_config.findType(DEFAULT_TYPE);
		}

		Group group = type.findGroup(groupName);

		if (group == null) {
			group = type.findGroup(DEFAULT_GROUP);
		}

		return group.findLevel(levelName);
	}

	public int querySuspendMinute(String typeName, String groupName, String levelName) {
		try {
			Level level = queryLevel(typeName, groupName, levelName);

			if (level == null) {
				return 0;
			} else {
				return level.getSuspendMinute();
			}
		} catch (Exception ex) {
			return 0;
		}
	}

	private boolean storeConfig() {
		synchronized (this) {
			try {
				Config config = m_configDao.createLocal();

				config.setId(m_configId);
				config.setKeyId(m_configId);
				config.setName(CONFIG_NAME);
				config.setContent(m_config.toString());
				m_configDao.updateByPK(config, ConfigEntity.UPDATESET_FULL);
			} catch (Exception e) {
				Cat.logError(e);
				return false;
			}
		}
		return true;
	}

}
