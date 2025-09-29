package com.thysmesi.services

import com.thysmesi.server.CallPisteHandler
import com.thysmesi.server.PisteHandler
import com.thysmesi.service.CallPisteService

data class PisteServiceInformationHandler(val otherHandlers: List<PisteHandler<*, *>>): CallPisteHandler<Unit, List<PisteServiceInformationService.ServiceInformation>> {
    override val service = PisteServiceInformationService

    override suspend fun handle(request: Unit): List<PisteServiceInformationService.ServiceInformation> {
        return otherHandlers.map { it.serviceInformation } + this.serviceInformation
    }
}

private val PisteHandler<*, *>.serviceInformation: PisteServiceInformationService.ServiceInformation
    get() = PisteServiceInformationService.ServiceInformation(id = service.id, title = service.title, description = service.description)