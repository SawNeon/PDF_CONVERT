package br.com.pdflocal.service;

import br.com.pdflocal.model.OfficeKind;
import java.nio.file.Path;
import java.util.Optional;

interface SystemProbe {

    boolean officeInstalled(OfficeKind app);

    boolean officeRunning(OfficeKind app);

    Optional<Path> libreOffice();

    Optional<Path> browser();
}
