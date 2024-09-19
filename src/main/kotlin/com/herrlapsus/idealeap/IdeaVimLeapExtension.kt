package com.herrlapsus.idealeap

import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.extension.VimExtension
import com.maddyhome.idea.vim.extension.VimExtensionFacade.putExtensionHandlerMapping
import com.maddyhome.idea.vim.extension.VimExtensionFacade.putKeyMappingIfMissing
import com.maddyhome.idea.vim.helper.StringHelper.parseKeys
import org.apache.batik.svggen.ExtensionHandler
import java.util.*

class IdeaVimLeapExtension : VimExtension {
    override fun getName(): String = "leap"

    override fun init() {
        // TODO: add keymappings for leap 's` and 'S` commands
    }

}

/**
 * Map some <Plug>(keys) command to given handler
 *  and create mapping to <Plug>(prefix)[keys]
 */
private fun VimExtension.mapToFunctionAndProvideKeys(
    keys: String, handler: ExtensionHandler, mappingModes: EnumSet<MappingMode>
) {
    putExtensionHandlerMapping(
        MappingMode.NXO,
        parseKeys(command(keys)),
        owner,
        handler,
        false
    )
    putKeyMappingIfMissing(
        MappingMode.NXO,
        parseKeys(value.toString()),
        owner,
        parseKeys(command(keys)),
        false
    )
}
private fun command(keys: String) = "<Plug>(leap-$keys)"