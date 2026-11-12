package com.instakill.notification;

/** Trimite o notificare catre un serviciu extern. Implementarile nu arunca exceptii. */
public interface NotificationSender {

    void send(String title, String message);
}
