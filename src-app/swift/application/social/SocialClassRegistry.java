/*****************************************************************************
 * Copyright 2011-2012 INRIA
 * Copyright 2011-2012 Universidade Nova de Lisboa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *****************************************************************************/
package swift.application.social;

import swift.application.social.crdt.AddWinsMessageSetCRDT;
import swift.application.social.crdt.AddWinsMessageSetUpdate;
import swift.application.social.crdt.LWWUserRegisterCRDT;
import swift.application.social.crdt.LWWUserRegisterUpdate;
import sys.net.impl.KryoClassRegistry;


public class SocialClassRegistry extends KryoClassRegistry {
    @Override
    public void registerClasses(Registrable reg) {
        reg.register(User.class);
        reg.register(Message.class);
        reg.register(LWWUserRegisterCRDT.class);
        reg.register(LWWUserRegisterUpdate.class);
        reg.register(AddWinsMessageSetCRDT.class);
        reg.register(AddWinsMessageSetUpdate.class);
    }
}
