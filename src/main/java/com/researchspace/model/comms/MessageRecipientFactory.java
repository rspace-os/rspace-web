package com.researchspace.model.comms;

import java.util.List;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * GEnerates to/cc/bcc list depending on the message type.
 */
public class MessageRecipientFactory {

	public void populateRecipients(List<String> addrs, MimeMessage message, Communication comm)
			throws MessagingException, AddressException {
		if (isGlobalMessage(comm)) {
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(comm.getOriginator().getEmail()));
			for (String to : addrs) {
				message.addRecipient(Message.RecipientType.BCC, new InternetAddress(to));
			}
		} else {
			for (String to : addrs) {
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
			}
		}
	}

	private boolean isGlobalMessage(Communication comm) {
		return comm != null && comm.isMessageOrRequest()
				&& ((MessageOrRequest) comm).getMessageType().equals(MessageType.GLOBAL_MESSAGE);
	}

}
