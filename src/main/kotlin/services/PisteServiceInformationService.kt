package com.thysmesi.services

import com.thysmesi.PisteId
import com.thysmesi.service.CallPisteService

data object PisteServiceInformationService: CallPisteService<Unit, List<PisteServiceInformationService.ServiceInformation>> {
    override val id: PisteId = 0x88888888u

    override val title: String = "Piste Service Information"
    override val description: String = "Responds with information about the currently supported services"

    data class ServiceInformation(
        val id: PisteId,
        val title: String,
        val description: String
    )
}