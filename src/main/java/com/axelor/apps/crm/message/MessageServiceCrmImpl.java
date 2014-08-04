/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2012-2014 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.crm.message;

import java.util.List;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.UserInfo;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.base.service.user.UserInfoService;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.IMessage;
import com.axelor.apps.message.db.MailAccount;
import com.axelor.apps.message.db.Message;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class MessageServiceCrmImpl extends MessageServiceBaseImpl {

	private static final Logger LOG = LoggerFactory.getLogger(MessageServiceCrmImpl.class);
	
	private DateTime todayTime;
	
	
	@Inject
	public MessageServiceCrmImpl(UserInfoService userInfoService) {

		super(userInfoService);
	}
	
	@Transactional
	public Message createMessage(Event event, MailAccount mailAccount)  {
		
		UserInfo recipientUserInfo = event.getUserInfo();
		
		List<EmailAddress> toEmailAddressList = Lists.newArrayList();
		
		if(recipientUserInfo != null)  {
			Partner partner = recipientUserInfo.getPartner();
			if(partner != null)  {
				EmailAddress emailAddress = partner.getEmailAddress();
				if(emailAddress != null)  {
					toEmailAddressList.add(emailAddress);
				}
			}
		}
		
		Message message = super.createMessage(
				event.getDescription(), 
				null, 
				IMessage.RELATED_TO_EVENT, 
				event.getId().intValue(), 
				event.getRelatedToSelect(), 
				event.getRelatedToSelectId(), 
				todayTime.toLocalDateTime(), 
				false, 
				IMessage.STATUS_SENT, 
				"Remind : "+event.getSubject(), 
				IMessage.TYPE_RECEIVED,
				toEmailAddressList,
				null,
				null,
				mailAccount,
				null, null, 0);
		
		message.setRecipientUserInfo(event.getResponsibleUserInfo());
		
		return message.save();
	}	
	
	
	
	
	
}